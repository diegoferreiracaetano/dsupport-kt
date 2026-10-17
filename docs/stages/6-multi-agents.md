# Stage 6 — Multi-agent orchestration

[English](6-multi-agents.md) | [Português](6-multi-agents.pt-BR.md)

## Goal

Turn the single agent into a **team**: an orchestrator that never touches data and three specialists (knowledge, orders, escalation), each with its own prompt and **only the tools of its job**. The orchestrator delegates by calling a specialist as if it were a tool. In the same stage the loop learns to run the tool calls of one turn **in parallel**, and the whole system is assembled for the first time, with the guards from stage 7, the review loop from stage 8.2 and a real end-to-end run against the API. You write this stage.

| Piece | Before | Stage 6 |
|---|---|---|
| `Agent` loop | tool calls of a turn run one after another | **run in parallel, results kept in order** |
| Specialists | do not exist | **`knowledge`, `orders`, `escalation`** |
| Orchestrator | does not exist | **delegates, never reads data** |
| Tool selection per agent | hard-coded in tests | **`config/agents.json` allow-list** |
| Assembly | none | **`cli` builds guards, agents and review loop** |

## Concept

A **sub-agent is a tool whose implementation is another agent**. To the orchestrator, delegating is just a tool call; to the specialist, the task arrives as its first user message. Three consequences you must design for:

1. **Specialists do not see the conversation.** The orchestrator must write each task complete (order number, email, what the customer wants). The prompt says so.
2. **Each specialist call needs a fresh `Agent`.** An `Agent` keeps its history in a field. If the orchestrator asks the `orders` specialist about two orders in the same turn and both calls share one instance, the two runs corrupt each other. `subAgentTool` therefore takes a **factory**, not an instance.
3. **Least privilege.** The orchestrator has no data tool at all; each specialist gets only the tools listed for it in the configuration. The allow-list is the point: a prompt can be talked around, a tool that is not there cannot.

Parallel tool calls: when one model reply contains several tool calls, they are independent by construction, so run them concurrently and send **all results back together, in the original order**. With `kotlinx.coroutines` that is `coroutineScope { calls.map { async { ... } }.awaitAll() }`.

## What to write

**1. `Agent` (modify).** Run the calls of a turn in parallel. Rules:

- The history receives `AssistantToolCalls` first and the `ToolResult` messages afterwards, **in the order of the calls**, whatever order they finished in.
- A tool failure must not cancel its siblings: it already becomes an error result. Do the same for an exception thrown by a **guard**: it becomes an error result for that call only. `CancellationException` is always rethrown.
- Every call still goes through the guards (`guarded(...)`), never directly to `tool.run`.

**2. `subAgentTool` (new, `core/agent`).**

```kotlin
fun subAgentTool(name: String, description: String, newAgent: () -> Agent): Tool
```

Input schema: one string, `task`. `run` creates an agent with `newAgent()`, runs it with the task and returns its text as the result. An exception inside the specialist becomes an error result. The specialist agent is created with `depth = 1`, so the trace shows the nesting.

**3. Configuration model and catalog (new, `core`).**

```kotlin
@Serializable data class AgentDef(val tools: List<String>, val maxTurns: Int = 4)
@Serializable data class AgentsConfig(val agents: Map<String, AgentDef>)

class ToolCatalog(private val providers: List<ToolProvider>) {
    suspend fun select(names: List<String>): List<Tool>
}
```

`select` returns the tools in the order of `names`. An unknown name throws `IllegalArgumentException` naming it. Two providers exposing the same tool name throw `IllegalStateException`. Providers are asked once.

`config/agents.json`:

```json
{
  "agents": {
    "knowledge":  { "tools": ["search_kb"] },
    "orders":     { "tools": ["get_order_status", "track_shipment"] },
    "escalation": { "tools": ["create_ticket"] }
  }
}
```

**4. Prompts (new, `core/agent/Prompts.kt`, all in English).** Constants `ORCHESTRATOR_PROMPT`, `KNOWLEDGE_PROMPT`, `ORDERS_PROMPT`, `ESCALATION_PROMPT` and `REVIEWER_PROMPT`. Starting drafts (adapt freely):

- Orchestrator: "You coordinate customer support. You never look up data yourself: always delegate to a specialist. `knowledge` answers policy questions. `orders` answers about status, delays and tracking. `escalation` opens a ticket for a human agent. Specialists cannot see the conversation, so write each task complete, with every detail they need. If the order number or the email is missing for an order question, ask the customer before delegating. If `orders` reports a delay, call `escalation` and tell the customer the ticket number. Write the final answer short, friendly and in English."
- Knowledge: "You answer policy questions. Use `search_kb` and answer only from what it returns, naming the article titles you used. If the search finds nothing relevant, reply exactly `NO_ANSWER`."
- Orders: "You look up the order given in the task using your tools and report only facts: status, delivery estimate, whether it is late and by how many days, and tracking if asked. If the order is not found, say so."
- Escalation: "You open tickets for human agents. Use `create_ticket` with a short objective summary, priority `high` for delays or unhappy customers (otherwise `medium`) and the customer email. Reply only with the ticket number."
- Reviewer: "You review support answers before they reach the customer. You do not talk to the customer. You receive the customer message, the specialist results and the proposed answer. Reject it if it states something the results do not support, leaves part of the question unanswered, promises something no policy or ticket backs, or is unfriendly or too long for a chat. Each problem must say what to fix. If everything is right, approve with an empty list."

**5. Assembly (new, `cli`).** A class that builds everything from its dependencies:

```kotlin
class DSupportAssembly(
    llm: LlmClient,
    toolProviders: List<ToolProvider>,
    agents: AgentsConfig,
    tickets: TicketGateway,
    maxRevisions: Int = 2,
) {
    suspend fun answerCustomer(): AnswerCustomer
}
```

- `answerCustomer()` selects the tools of each specialist through `ToolCatalog`, builds the **guards** `[Redaction, IdentityGate, RedactResults, MentionTicket, TicketDedup]` (`TicketDedup` **last**, it holds a reservation) and calls `GuardPolicy().validate(allSelectedTools, guards)` once.
- The `AnswerCustomer` factory receives the `EventSink` of each question and builds, **per question**, the three specialist factories (each new agent gets `hooks = guards`, `events = sink`, `depth = 1`), the orchestrator (`tools` = only the `subAgentTool`s, `maxTurns = 8`, the same guards) and the `ReviewLoop`.
- The orchestrator must have no data tool. If the configuration gives it one, fail at assembly.

**6. Entry point (`cli/Main.kt`).** Read the question from the arguments. Environment: `ANTHROPIC_API_KEY` (required), `ANTHROPIC_MODEL` (default `claude-haiku-5-5`), `DATA_DIR` (default `data`). Build the `HttpClient` (CIO), `AnthropicLlmClient`, the knowledge base (`KbLoader` + `VectorKnowledgeBase` with `TfIdfEmbedder`), the JSON gateways, the providers and the assembly. Collect the `Flow<AgentEvent>`, print one line per event (agent, what it did, tokens) and the final `SupportAnswer` as pretty JSON.

- Providers: `LocalToolProvider(listOf(SearchKbTool(kb), CreateTicketTool(tickets)))` for knowledge and escalation. For orders use `LocalToolProvider(GetOrderStatusTool, TrackShipmentTool)` until stage 5 is done; after it, replace that single line with `McpToolProvider(config/mcp-servers.json)` and nothing else changes.
- **Reference date:** "late" depends on today. Support an optional `REFERENCE_DATE` (ISO date) environment variable and pass it as the `today` of `GetOrderStatusTool`, also inside the `mcp-orders` server. The evaluation will fix it to `2026-10-15` so results never drift.

## Tests (write them first)

One class per production class, MockK for collaborators, names `given … when … then should …`, no comments.

**`AgentTest`** (add to the existing class):

- given two tool calls in one turn when running then should run them in parallel (`runTest`, each mocked tool with `delay(1000)`, assert `testScheduler.currentTime == 1000`, not 2000)
- given two tool calls where the second finishes first when running then should send the results back in the order of the calls
- given two tool calls where one throws when running then should still run the other and report the failure as an error result
- given a hook that throws for one call when running then should turn it into an error result for that call only
- given several tool calls when running then should consult the hooks once per call

**`SubAgentToolTest`**: given a task when running then should create a new agent, run it with that task and return its answer; given two simultaneous calls then should create two distinct agents; given a specialist that throws then should return an error result; given the tool when reading its spec then should require a `task` string.

**`ToolCatalogTest`**: select by names in order; unknown name → `IllegalArgumentException` naming it; two providers with the same tool name → `IllegalStateException`; providers are consulted only once.

**`AgentsConfigTest`**: given the project `config/agents.json` when decoding then should list the three specialists with their tools.

**`DSupportAssemblyTest`** (`cli/src/test`): given the standard configuration when assembling then should give the orchestrator only sub-agent tools; given a configuration that gives the orchestrator a data tool then should fail; given no guard covering `create_ticket` then should fail with the tool name.

**`EscalationAgentTest`** (`cli/src/test`, real `JsonTicketGateway` on a temp file, real `CreateTicketTool`, `TicketDedup`, `Redaction`, mocked `LlmClient`): given a reply with two `create_ticket` calls for the same customer when the agent runs then should create exactly one ticket and block the other. This is the end-to-end proof of the parallel-ticket requirement.

## Run

```bash
./gradlew test
export ANTHROPIC_API_KEY=...        # your key, never in a file that is committed
REFERENCE_DATE=2026-10-15 ./gradlew :cli:run --args="My order 1042 has not arrived. My email is ana@example.com"
```

Expected on the late order: the orchestrator asks `orders` for the status, sees 10 days of delay, asks `escalation` for a ticket, the answer names the ticket (`TCK-...`), `data/tickets.json` gains one entry (**reset it to `[]` before committing**). Try also a policy question (only `knowledge` is called) and a question without an email (the orchestrator asks the customer, no data tool is called).

## What to observe

- The events in order: which agent called which, what ran in parallel, which guard acted (`GuardActed`), how many review rounds.
- The tokens per agent: the orchestrator is the expensive one, because it carries the whole conversation.
- That the orchestrator never calls a data tool, and that a specialist cannot call a tool outside its list.

## Exercises (optional)

- Give the `knowledge` specialist a lower `maxTurns` and see what a loop that is too short does to the answer.
- Swap `ANTHROPIC_MODEL` for another model and compare cost and quality of the same question.
- Add a fourth specialist by editing only `config/agents.json` and the assembly.

## How to explain it in an interview

"A sub-agent is just a tool whose implementation is another agent, so the orchestrator needs no special mechanism to delegate. I give every specialist only the tools of its job through an allow-list, because a missing tool is a stronger guarantee than a prompt. Tool calls of one turn run in parallel and come back in order, and each call gets a fresh agent so concurrent runs cannot share history."
