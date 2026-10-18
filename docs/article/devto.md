---
title: "AI in Practice: RAG, MCP and Multi-Agent Systems in Kotlin, Without Magic Frameworks"
published: false
description: "Real code, real numbers and an uncomfortable question: should you leave everything to the model? A multi-agent support assistant in Kotlin, explained line by line."
tags: ai, kotlin, architecture, llm
cover_image: https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/cover.png
---

# AI in Practice: Demystifying RAG, MCP and Multi-Agent Systems in Kotlin, Without "Magic" Frameworks

> ❗ **Opening provocation:** The market was flooded with buzzwords: RAG, MCP, multi-agent architecture, ReAct, guardrails. Every day a new framework promises autonomous agents in two lines of code. But what happens when you put that "demo agent" in front of a real customer support desk? It hallucinates, ignores business rules and leaks data. Enough *vibes-based engineering*!

This article walks through a real project, **DSupport**, built by hand in Kotlin Multiplatform with no agent framework. Every concept comes with the **real code** from the repository and an explanation of how it works, line by line.

📦 Full code: [github.com/diegoferreiracaetano/dsupport-kt](https://github.com/diegoferreiracaetano/dsupport-kt). Every stage is a git tag (`etapa-1` to `etapa-12`), so you can see the exact code at that point.

**How to read the code snippets:** the numbers ①②③ in the comments show up again in the explanation right below, under **🔍 Reading the code**.

---

## 1️⃣ The Big Illusion of "Everything Is an LLM": Deterministic vs. Non-Deterministic

There is an illusion that the language model should make 100% of a software's decisions. But an LLM is a **probabilistic** engine: the same input can produce different outputs. Asking it, through a prompt, to never show sensitive data is Russian roulette.

```text
+-----------------------------------------------------------------------+
|                        HYBRID SYSTEM (DSupport)                        |
+------------------------------------+----------------------------------+
|  Model (non-deterministic)         |  Code (deterministic)            |
+------------------------------------+----------------------------------+
|  - understand natural language     |  - validate types and rules      |
|  - decide which tool to call       |  - hide sensitive data           |
|  - draft the reply                 |  - block what must not pass      |
|  - route between specialists       |  - tests that run offline        |
+------------------------------------+----------------------------------+
```

> ⚠️ **Why does relying on the prompt alone fail in production?** A prompt is a suggestion, not a guarantee. A prompt injection or a plain stochastic variation breaks the rule. Whatever matters to the business must be enforced **in code**.

### The contrast: three ways to build it

| Approach | What it delivers | Where it breaks |
| :--- | :--- | :--- |
| **100% deterministic** (`if/else`, state machines) | Predictable, fast, zero cost | It does not understand paraphrase; the rule list grows forever |
| **100% model** ("magic" framework) | Flexible, looks smart | Unpredictable answers, endless loops, runaway cost |
| **Hybrid (DSupport)** | The model understands and chooses; the code validates and blocks | Costs more than an `if`: it needs evals and tracing |

### In practice: where the deterministic version fails

This is the first thing anyone would try, and it is a great idea for predictable questions:

```kotlin
// A deterministic router (illustration, not in the repository)
fun route(message: String): Intent = when {
    "refund" in message.lowercase()                     -> Intent.POLICY   // ①
    Regex("""order\s*#?\d+""").containsMatchIn(message) -> Intent.ORDER    // ②
    else                                                -> Intent.OTHER    // ③
}
```

**🔍 Reading the code**
1. **①** It looks for the exact word "refund". "I want my money back" does not contain it.
2. **②** It requires the pattern "order 1042". "Where is the thing I bought Monday?" does not match.
3. **③** Anything not predicted lands here, silently.

```text
"my package never showed up"           -> OTHER   (wrong)
"where is the thing I bought Monday?"  -> OTHER   (wrong)
"I want my money back"                 -> OTHER   (wrong)
```

> 💡 The deterministic version fails on **language**, not on logic. The agent fails on **trust**. The right question is not "AI or code?" but **"what does each one do best?"**. In DSupport, **5 roles use a model** and **14 pieces are plain code** (guardrails, validator, TF-IDF search, tools, MCP transport, tracing, eval scoring and more).

---

## 2️⃣ The DSupport Architecture: Clean Separation of Responsibilities

For testability and vendor independence (no *vendor lock-in*), DSupport uses **Clean Architecture** and **Ports & Adapters** in Kotlin Multiplatform. The golden rule: **dependencies point inward**.

![Module dependencies](https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/modules.png)

### 🧱 How the modules are separated

1. **`contract`**: the shared types (`SupportAnswer`, `AgentEvent`). No external dependencies.
2. **`core`**: the heart. Agent loop, orchestration, guardrails, review and the **ports** (interfaces). It does not know what HTTP, Anthropic or JSON is.
3. **`adapters`**: implement the ports. Anthropic client, RAG engine, JSON files, MCP client. Swapping Claude for another model means writing **a new adapter**, without touching `core`.
4. **`mcp-orders` and `mcp-tickets`**: independent servers that speak the MCP protocol.
5. **`cli`, `server`, `app`**: entry points (terminal, Ktor API with SSE, Compose app on desktop and iPhone).
6. **`eval`**: the evaluation suite with a *golden dataset*.

### A real port: what `core` knows about the model

```kotlin
interface LlmClient {
    suspend fun next(request: LlmRequest): LlmReply                                      // ①
    suspend fun structured(request: LlmRequest, schema: OutputSchema): StructuredReply   // ②
}
```

**🔍 Reading the code**
1. **①** `next` returns **text** or **a tool request** (`LlmReply.Text` or `LlmReply.ToolCalls`). It is the basis of the agent loop.
2. **②** `structured` forces the model to fill in a JSON form (we will see it in section 3).
3. There is no provider `import` here. `AnthropicLlmClient`, in the `adapters` module, implements this interface and translates everything to the Anthropic JSON, with 2 retries on 429 and 5xx errors.

> ℹ️ **Why does this matter?** The project's 380+ tests run **offline**, with test doubles in place of the model. No test spends a cent on the API.

---

## 3️⃣ The Concepts Mapped in Practice (From Theory to Code)

### 🔹 Agent loop: the model decides, the code acts (`etapa-1`)

**In one sentence:** a `for` loop with a limit. The model asks for a tool, the code runs it, the result goes back, and the cycle repeats until there is an answer.

![Agent loop](https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/panel-1.png)

```kotlin
private suspend fun loop(): String {
    repeat(maxTurns) {                                                              // ①
        val reply = llm.next(LlmRequest(system, history.toList(), specs))           // ②
        when (reply) {
            is LlmReply.Text -> return finalAnswer(reply.text)                      // ③
            is LlmReply.ToolCalls -> {
                history += Message.AssistantToolCalls(reply.calls)
                val results = coroutineScope {
                    reply.calls.map { call -> async { execute(call.tool, call.args) } }.awaitAll()   // ④
                }
                reply.calls.zip(results).forEach { (call, result) ->
                    history += Message.ToolResult(call.id, call.tool, result.content, result.isError) // ⑤
                }
            }
        }
    }
    return "Turn limit of $maxTurns reached. Escalating to a human agent."          // ⑥
}
```

**🔍 Reading the code**
1. **①** `repeat(maxTurns)`: the loop **always** has a limit. Without it, a confused model calls tools forever.
2. **②** On every turn the whole history is sent again, together with the list of available tools.
3. **③** If the model answered with **text**, it is over. Before returning, the answer goes through the guardrails (`finalAnswer`).
4. **④** If it asked for **tools**, they run **in parallel** (`async` + `awaitAll`). Two independent lookups take as long as the slower one.
5. **⑤** Results enter the history **in the same order as the requests**, because the model API requires that match.
6. **⑥** Limit reached? The code returns an escalation message. The model does not decide that.

> 🛑 The model **never executes anything**. It only **asks**. Your code executes, validates and decides what comes back.

---

### 🔹 Tools (function calling): giving the model hands (`etapa-3`)

A tool is a function with a **name**, a **description** and an **input schema** (JSON Schema). The model only sees that; the code does the work.

```kotlin
interface Tool {
    val spec: ToolSpec                                   // ① name, description, schema
    suspend fun run(args: JsonObject): ToolResult        // ② executes
}
```

```kotlin
override suspend fun run(args: JsonObject): ToolResult {
    val orderId = args.stringArg("order_id") ?: return fail("missing_argument:order_id")     // ①
    val email = args.stringArg("email") ?: return fail("missing_argument:email")
    val order = orders.findOwned(orderId, email)                                            // ②
        ?: return fail("order_not_found_or_email_mismatch")
    val late = order.status == OrderStatus.IN_TRANSIT && order.eta < today()                // ③
    return ok("status" to JsonPrimitive(order.status.wire), "late" to JsonPrimitive(late))  // ④
}
```

**🔍 Reading the code (`GetOrderStatusTool`, shortened)**
1. **①** Missing argument? It returns a **readable error result**, not an exception. The model reads `missing_argument:email` and **asks the customer for the email**.
2. **②** `findOwned` only returns the order if the number **and** the email match. This is the first lock against data leaks.
3. **③** The delay is **computed in code**, from the estimated date. The model does not "guess" that it is late: it reads `late: true`.
4. **④** The return is short JSON, already in the right shape, without dumping the whole database into the context.

> 💡 The tool **description** is part of the prompt. A vague description makes the model pick the wrong tool. Write it as if it were instructions for an intern.

---

### 🔹 RAG (Retrieval-Augmented Generation): answering with a consultation (`etapa-4`)

Instead of letting the model invent the return policy, RAG **searches** the help center and forces it to answer **only from what was found**.

![RAG](https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/panel-4.png)

```kotlin
class SearchKbTool(
    private val kb: KnowledgeSearch,
    private val minScore: Double = 0.25,                                  // ①
) : Tool {
    override suspend fun run(args: JsonObject): ToolResult {
        val query = args.stringArg("query") ?: return fail("missing_argument:query")
        val relevant = kb.search(query).filter { it.score >= minScore }   // ②
        if (relevant.isEmpty()) return fail("nothing_relevant_found")     // ③
        val results = relevant.map {
            buildJsonObject { put("doc_id", it.docId); put("title", it.title); put("snippet", it.snippet); put("score", it.score) }  // ④
        }
        return ok("results" to JsonArray(results))
    }
}
```

**🔍 Reading the code**
1. **①** The **relevance cut-off** is the most important piece. Without it, the search always returns *something*.
2. **②** Only what is similar enough to the question gets through.
3. **③** Nothing relevant? An **honest** answer: "nothing found". The knowledge specialist is instructed to reply `NO_ANSWER` in that case, instead of inventing.
4. **④** Every result carries the **article title**. That is how the final answer can **cite the source**, and the validator (below) checks that the cited source really came from the search.

Articles are split by section (`MarkdownChunker`) and turned into vectors with **TF-IDF**, a simple word weighting. There is an optional `VoyageEmbedder` for semantic search. Notice: the search itself is **deterministic code**, and it works without an API key.

> ⚠️ RAG without a relevance cut-off is the number one cause of "confident answers based on a passage that has nothing to do with the question".

---

### 🔹 MCP (Model Context Protocol): tools in separate processes (`etapa-5`)

MCP is an open standard: tools live in **other programs** and speak JSON-RPC. In this project, over **stdio** with the official Kotlin SDK: the client starts the server as a child process. A session has 3 messages: `initialize`, `tools/list`, `tools/call`.

```json
{
  "servers": [
    { "name": "orders",  "command": ["mcp-orders/build/install/mcp-orders/bin/mcp-orders"],   "env": { "DATA_DIR": "data" } },
    { "name": "tickets", "command": ["mcp-tickets/build/install/mcp-tickets/bin/mcp-tickets"], "env": { "DATA_DIR": "data" } }
  ]
}
```

On the server side, **any list of `Tool`** becomes an MCP server:

```kotlin
server.addTool(
    name = spec.name,
    description = spec.description,
    inputSchema = ToolSchema(properties = spec.inputSchema["properties"] as? JsonObject, required = ...),   // ①
    toolAnnotations = ToolAnnotations(readOnlyHint = spec.readOnly, destructiveHint = spec.destructive),    // ②
) { request ->
    val result = tool.run(request.arguments ?: JsonObject(emptyMap()))                                       // ③
    CallToolResult(content = listOf(TextContent(result.content)), isError = result.isError)                  // ④
}
```

**🔍 Reading the code (server)**
1. **①** The same `ToolSpec` of the local tools is converted to the protocol schema.
2. **②** The `readOnly` and `destructive` flags travel along: a client can use them to ask for confirmation.
3. **③** The tool is the **same class** as before. Nothing was rewritten to become remote.
4. **④** The result goes back as text, with the error flag.

On the client side, a remote failure **must never** freeze the agent:

```kotlin
override suspend fun run(args: JsonObject): ToolResult = try {
    withTimeout(timeoutMs) { connection.callTool(spec.name, args) }     // ①
} catch (e: CancellationException) {
    if (e is TimeoutCancellationException) ToolResult("error: ${spec.name} timed out", isError = true) else throw e   // ②
} catch (e: Exception) {
    ToolResult("error: ${e.message}", isError = true)                    // ③
}
```

**🔍 Reading the code (client)**
1. **①** Every remote call has a **timeout**. A slow server does not freeze the loop.
2. **②** A timeout becomes an **error result**. A real cancellation (`CancellationException`) is rethrown, so coroutines do not break.
3. **③** A dead server or an exception also becomes an error the model can read and work around.

> 💡 **MCP benefit:** adding the tickets server is **one line in `config/mcp-servers.json`**. The agent code does not change: it does not know whether a tool is local or remote.

> 🛑 On an MCP server over stdio, `stdout` **is the protocol**. A stray `println` corrupts the conversation. That is why the server redirects `System.out` to `System.err`.

---

### 🔹 Multi-agent orchestration: one orchestrator, three specialists (`etapa-6`)

Instead of one giant "super prompt", an **orchestrator** with a senior-support-specialist profile delegates to agents with a **short prompt** and **only the tools they need**.

![Multi-agent orchestration](https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/panel-6.png)

Who may use what is **configuration**, not code:

```json
{
  "agents": {
    "knowledge":  { "tools": ["search_kb"] },
    "orders":     { "tools": ["get_order_status", "track_shipment"] },
    "escalation": { "tools": ["create_ticket"] }
  }
}
```

And each specialist is exposed to the orchestrator **as if it were a tool**:

```kotlin
class SubAgentTool(
    private val name: String,
    description: String,
    private val newAgent: () -> Agent,                           // ①
) : Tool {
    override val spec = ToolSpec(name, description, stringSchema("task"))

    override suspend fun run(args: JsonObject): ToolResult {
        val task = args.stringArg("task") ?: return fail("missing_argument:task")
        return try {
            ToolResult(newAgent().run(task))                     // ②
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult("error: specialist '$name' failed: ${e.message}", isError = true)   // ③
        }
    }
}
```

**🔍 Reading the code**
1. **①** It receives an agent **factory**, not an agent. Every call creates a **new** agent, because an agent keeps its history and two parallel calls must not share it.
2. **②** Running the specialist is just calling `run(task)`. To the orchestrator, this is a tool like any other.
3. **③** If the specialist fails, the orchestrator receives an error and moves on.

The orchestrator has no data tools. Its profile, in the prompt, starts like this:

```text
You are a senior customer support specialist and the coordinator of a small team. [...]
You never look up data yourself: you decide who handles each part and always delegate to a specialist.
[...]
- Never promise anything the specialists did not confirm: no dates, refunds or outcomes of your own.
```

> ⚠️ If you also give the data tools to the orchestrator, it **skips delegation** and the design collapses. That is why `DSupportAssembly` fails to assemble the system if the orchestrator has tools.

---

### 🔹 Guardrails: rules the model can neither see nor skip (`etapa-7`)

Guardrails are **deterministic code** that runs at 3 fixed points of the loop. Every guardrail implements this interface:

```kotlin
interface Hook {
    val covers: Set<String> get() = emptySet()                                                              // ①
    suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision = Decision.Allow      // ②
    suspend fun afterTool(agent: String, tool: ToolSpec, args: JsonObject, result: ToolResult): ToolResult = result   // ③
    suspend fun beforeFinal(agent: String, history: List<Message>, text: String): String = text            // ④
}
```

**🔍 Reading the code**
1. **①** Which tools the guardrail protects.
2. **②** **Before** the tool: it can **allow**, **deny** or **modify** the arguments.
3. **③** **After** the tool: it can **rewrite the result** (for example, hide personal data).
4. **④** **Before the final answer**: it can rewrite it.

A concrete example: `IdentityGate` only lets an order lookup through with a valid number and email.

```kotlin
override suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision = when {
    tool.name !in covers -> Decision.Allow                                                                   // ①
    args.stringArg("order_id")?.let { digits.matches(it) } != true ->
        Decision.Deny("order_id is missing or invalid. Ask the customer for the order number.")              // ②
    args.stringArg("email")?.let { Patterns.email.matches(it) } != true ->
        Decision.Deny("email is missing or invalid. Ask the customer for the email used in the purchase.")
    else -> Decision.Allow                                                                                   // ③
}
```

**🔍 Reading the code**
1. **①** Other tools pass straight through.
2. **②** The denial carries an **instruction for the model** ("ask the customer for the number"). The model reads the reason and acts, instead of retrying blindly.
3. **③** Only what passed both checks reaches the tool. The model **cannot get around it**: the guardrail runs in code, not in the prompt.

And the case that proves a prompt is not enough: **two parallel `create_ticket` calls**.

```kotlin
override suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision {
    val email = customerOf(tool, args) ?: return Decision.Allow
    return lock.withLock {                                                                         // ①
        if (email in inFlight) {
            return@withLock Decision.Deny("A ticket for this customer is already being created. Do not open another.")  // ②
        }
        val existing = tickets.findOpenByEmail(email)
        if (existing != null) {
            return@withLock Decision.Deny("Ticket ${existing.ticketId} is already open for this customer. [...]")        // ③
        }
        inFlight += email                                                                          // ④
        Decision.Allow
    }
}
```

**🔍 Reading the code (`TicketDedup`)**
1. **①** A `Mutex` guarantees only one call at a time enters this check.
2. **②** A creation is already **in progress** for this customer? Deny.
3. **③** A ticket is already **open**? Deny and give the number, so the model can tell the customer.
4. **④** Before allowing, it **reserves** the customer. That is what stops two simultaneous calls from both passing.

> 🛑 "Please never create duplicate tickets" in the prompt is a **wish**. The `Mutex` is a **guarantee**.

How guardrails plug into the loop (a snippet of `Agent.guarded`):

```kotlin
for (hook in hooks) {
    when (val decision = hook.beforeTool(name, tool.spec, args)) {
        Decision.Allow -> Unit
        is Decision.Deny -> return ToolResult("blocked by ${hook.name}: ${decision.reason}", isError = true)   // ①
        is Decision.Modify -> args = decision.args                                                             // ②
    }
}
```

1. **①** Denied: the tool **does not even run**, and the model receives the reason.
2. **②** Modified: the tool runs with the corrected arguments.

---

### 🔹 Structured output and validation in code (`etapa-8.1`)

The final answer **is not a paragraph**: it is a JSON form.

```kotlin
data class SupportAnswer(
    val answer: String = "",
    val intent: String = "other",
    val sources: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val escalated: Boolean = false,
    @SerialName("ticket_id") val ticketId: String? = null,
)
```

But a schema only guarantees the **shape**, not the **truth**. Code checks the truth:

```kotlin
fun problems(answer: SupportAnswer, evidence: List<String>): List<String> = buildList {
    val results = evidence.joinToString("\n")                                                      // ①
    if (answer.intent !in INTENTS) add("invalid intent: ${answer.intent}")                         // ②
    if (answer.confidence !in 0.0..1.0) add("confidence out of range 0..1: ${answer.confidence}")  // ③
    answer.ticketId?.let { ticket ->
        if (ticket !in results) add("ticket $ticket does not appear in the tool results (invented?)")   // ④
        if (ticket !in answer.answer) add("ticket $ticket is missing from the customer-facing text")
    }
    answer.sources.filter { !results.contains(it, ignoreCase = true) }
        .forEach { add("source \"$it\" does not appear in the tool results") }                     // ⑤
}
```

**🔍 Reading the code**
1. **①** `evidence` is everything the tools returned in that conversation: the **source of truth**.
2. **②** The intent must be in the closed list (`policy`, `order`, `return`, `complaint`, `other`).
3. **③** The JSON schema cannot express a range, so the confidence range is checked here.
4. **④** **An invented ticket is rejected:** the number must have appeared in the tool output, and must also be in the text the customer will read.
5. **⑤** **An invented source is rejected:** you may only cite what the search really returned.

Every problem found is a sentence in English, written for the **model to read and fix**.

---

### 🔹 Producer–reviewer loop, with a limit (`etapa-8.2`)

A second model reviews the draft. But **the reviewer is a model too**, so the code checks first:

```kotlin
suspend fun answer(question: String): SupportAnswer {
    producer.run(question)
    for (round in 0..maxRevisions) {                                                                  // ①
        val draft = decode(producer.finish(SupportAnswerSchema.output))                               // ②
        val problems = validator.problems(draft, producer.evidence).ifEmpty { review(question, draft) }  // ③
        events.emit(AgentEvent.ReviewRound(round + 1, problems.isEmpty(), problems))                  // ④
        if (problems.isEmpty()) return draft                                                          // ⑤
        producer.followUp("A reviewer rejected your answer. Fix these points and answer the customer again:\n" +
            problems.joinToString("\n") { "- $it" })                                                  // ⑥
    }
    return escalate(question, intent)                                                                 // ⑦
}
```

**🔍 Reading the code**
1. **①** At most `maxRevisions` (2) fixes. This **limit** is what stops the endless loop.
2. **②** The producer hands in the draft in the structured format.
3. **③** **Code first** (cheap and exact). **Only if it passes**, the reviewer model judges the claims.
4. **④** Every round becomes an event, and shows up in the trace.
5. **⑤** Approved: return it.
6. **⑥** Rejected: the problems go back to the producer, with instructions to fix.
7. **⑦** Limit reached? **The code** opens the ticket for a human. The model does not decide to escalate.

![Producer-reviewer loop](https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/panel-8-2.png)

---

### 🔹 Observability: an airplane black box (`etapa-9.1`)

With agents running in parallel, "it went wrong" is not enough. Every event carries a `runId` and a `parentRunId`, and the tree rebuilds itself. The trick: the parent travels in the **coroutine context**, with no parameter passed at all.

```kotlin
class RunContext(val runId: String, val parentRunId: String?) : AbstractCoroutineContextElement(Key) {   // ①
    companion object Key : CoroutineContext.Key<RunContext>
}

suspend fun run(task: String): String {
    parentRunId = coroutineContext[RunContext]?.runId        // ②
    runId = runIds.next()                                    // ③
    return inRun { events.emit(AgentEvent.AgentStarted(name, depth, task, runId, parentRunId)); loop() }   // ④
}
```

**🔍 Reading the code**
1. **①** `RunContext` is a coroutine-context element that stores "who am I and who called me".
2. **②** A sub-agent is born **inside** the parent's tool call, so it finds the parent's `RunContext` and reads its id.
3. **③** Every run gets a unique id (`r1`, `r2`...), safe for concurrent calls.
4. **④** `inRun` installs the new context for everything this agent does next.

The result, generated only from the list of events:

```text
[orchestrator] r1
  model call: 900 in / 80 out tokens, 700 ms
  tool orders: 1300 ms
    [orders] r2
      model call: 400 in / 40 out tokens, 500 ms
```

---

### 🔹 Server and app: the same stream of events, now on a screen (`etapa-11` and `etapa-12`)

```kotlin
call.respondTextWriter(ContentType.Text.EventStream) {
    answerCustomer(question)
        .catch { failure -> write(ServerSentEvents.errorFrame(failure.message ?: "")); flush() }   // ①
        .collect { event ->
            write(ServerSentEvents.frame(event))                                                    // ②
            flush()                                                                                 // ③
        }
}
```

**🔍 Reading the code**
1. **①** A failure midway becomes an `event: error` frame, instead of a connection that dies silently.
2. **②** Every `AgentEvent` becomes **one SSE frame** (`data: {...}`).
3. **③** The `flush` is what makes the app show the **live trace**: without it, everything would arrive at once at the end.

The API key stays **only on the server**. The app (Compose, desktop and iPhone) reads the same stream and draws the conversation, the sources, the escalation badge and the trace.

---

## 4️⃣ Anatomy of a Real Run: The Producer–Reviewer Loop and Guardrails

The customer writes: `"My order 1042 has not arrived. My email is ana@example.com"`.

![Where the model is and where code is](https://raw.githubusercontent.com/diegoferreiracaetano/dsupport-kt/main/docs/article/medium/img/en/where-ai.png)

### 🔬 The real terminal trace

```text
$ REFERENCE_DATE=2026-10-15 ./gradlew :cli:run --args="My order 1042 has not arrived. My email is ana@example.com"

[orchestrator] calls orders {"task":"Check the status, tracking and delivery progress..."}
[orders] calls get_order_status {"order_id":"1042","email":"ana@example.com"}
[orders] calls track_shipment   {"order_id":"1042","email":"ana@example.com"}     <- in parallel
[orders] get_order_status finished in 12 ms: {"ok":true,"status":"in_transit","late":true,"days_late":10}
[orchestrator] calls escalation {"task":"Delayed order. Order number: 1042 ..."}
[escalation] create_ticket finished in 17 ms: {"ok":true,"ticket_id":"TCK-1001"}
review round 1: rejected (4 problems)
review round 2: approved
```

**What each line shows**
1. The two lookups of the `orders` specialist leave **together**, because the loop runs tools in parallel.
2. `late: true, days_late: 10` was **computed by the code**, not guessed by the model.
3. The delay triggered the `escalation` specialist, which opened ticket `TCK-1001`.
4. The reviewer **rejected the first draft** and approved the second. That is the producer–reviewer loop at work.

> ℹ️ **Efficiency and cost:** this run involved 12 model calls, about 15 thousand input tokens and 2 thousand output tokens, costing about **US$ 0.0025** (a quarter of a cent) with `claude-haiku-5-5`.

---

## 5️⃣ Continuous Evaluation (Evals): How to Prove Your Agent Works

If you do not measure the agent, you only have opinions. The `eval` module has a *golden dataset* of **15 cases** run **3 times each**, because the model is not deterministic: with the same code, 3 cases changed outcome between two single runs.

Each case is just data:

```json
{
  "id": "g01",
  "question": "How many days do I have to return an item?",
  "expected_intents": ["return", "policy"],
  "should_escalate": false,
  "must_contain": ["30 days"],
  "expected_sources": ["Returns Policy"]
}
```

**🔍 Reading the case**
1. `expected_intents`: did the orchestrator route correctly?
2. `should_escalate`: did it hand over to a human (or not) at the right time?
3. `must_contain`: is the vital fact (30 days) in the answer?
4. `expected_sources`: did the answer cite the right source?
5. There is also `must_not_contain`, to check for **leaks**.

### 📊 Real evolution and metrics of the project

| Metric | Before | After | What it means in practice |
| :--- | :---: | :---: | :--- |
| **Routing accuracy** | 100% | **100%** | The orchestrator sends the question to the right specialist |
| **Groundedness** | 100% | **100%** | No answer invents facts outside the sources |
| **No leaks** | 100% | **100%** | Customer data does not show up where it should not |
| **Expected facts** | 93% | **100%** | The vital information was delivered |
| **Cited sources** | 86% | **100%** | You can see where the answer came from |
| **Escalation precision** | 80% | **100%** | Hands over to a human at the right time (recall: 100%) |
| **Fallback after failed review** | 9% | **0%** | Answers refined without hitting the limit |

### 🔎 What the evals discovered (and code review never would)
1. **Two of my own rules contradicted each other.** `confidence` treated "asking the customer for a detail" as uncertainty, and a validator rejected low confidence.
2. **The reviewer invented objections.** It rejected "please send your email" because it did not know the assistant can ask for that.
3. **A deterministic routing error.** "Where do I find my tracking code?" always went to `orders`, instead of the help center.
4. **A test case that was just my assumption.** The case demanded an escalation that the assistant, correctly, avoided.

Total cost: about **30 cents of a dollar**, one afternoon.

---

## 🧪 Tests and Containerization

One test class per production class, with test doubles at the ports. **Zero API calls.**

```kotlin
@Test
fun `given a malformed email when checking an order tool then should deny`() = runTest {
    val decision = gate.beforeTool("orders", spec("get_order_status"),
        argsOf("order_id" to "1042", "email" to "not-an-email"))     // ①

    assertIs<Decision.Deny>(decision)                                // ②
}
```

1. **①** The test name reads like a sentence: *given / when / then*. It documents the rule.
2. **②** No model, no network: the guardrail is plain code, so the test runs in milliseconds.

A multi-stage `Dockerfile` for the server (build with the JDK, run with the JRE):

```dockerfile
# ① build stage, with the full JDK
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:installDist --no-daemon

# ② final image, much smaller, JRE only
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server/ ./
COPY config ./config
COPY data ./data
ENV DATA_DIR=data
EXPOSE 8080
# ③ starts the server
CMD ["bin/server"]
```

1. **①** The full JDK exists only to compile.
2. **②** The final image carries only the JRE and the build output.
3. **③** The API key comes in through an environment variable (`-e ANTHROPIC_API_KEY`), never inside the image.

```bash
./gradlew test    # 380+ tests, offline, without spending 1 cent on the API
```

---

## 🏁 Provocative Conclusion: The Future of AI in Engineered Software

Software engineering did not change just because we now use AI. Agents without **strong typing**, **clean architecture**, **guardrails in code** and **automated evaluation** are unpredictable toys.

> 🛑 **The price of "multi":** one delayed-order question cost **12 model calls** and about 10 seconds. Multi-agent is not free. Use one agent with tools when the task is small, and split into specialists only when prompts and toolsets grow and the roles are clearly different.

The real innovation is not the newest framework on Twitter. It is knowing **where to use the model's non-determinism** (understanding language, writing the reply) and **where to use the determinism of code** (validating, blocking, measuring).

### 💬 Your turn
1. Which rule in **your** project could be code instead of a prompt? Tell me in the comments.
2. Do you disagree that most of an agent should be deterministic? I want to hear the counter-example.
3. Share this with someone about to build "an AI agent". A star on the repository helps: [github.com/diegoferreiracaetano/dsupport-kt](https://github.com/diegoferreiracaetano/dsupport-kt)

📖 *Leia em português:* [docs/article/artigo-multiagentes-kotlin.md](https://github.com/diegoferreiracaetano/dsupport-kt/blob/main/docs/article/artigo-multiagentes-kotlin.md)
