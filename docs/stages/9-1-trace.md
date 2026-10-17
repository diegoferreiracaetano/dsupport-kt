# Stage 9.1 — Observability: events, tracing and cost per agent

[English](9-1-trace.md) | [Português](9-1-trace.pt-BR.md)

## Goal

Turn the stream of `AgentEvent`s into something a person can read: a **trace tree** showing which agent called which, how long each step took, and a **cost table** showing the tokens each agent spent. There is no global state: everything is computed from the list of events of one question. You write this stage.

| Piece | Before | Stage 9.1 |
|---|---|---|
| Events | flat list; the agent name and `depth` | **each event says which run it belongs to (`runId`), and sub-agent runs know their parent (`parentRunId`)** |
| Trace tree | none | **`TraceReport.tree`** |
| Cost per agent | none | **`TraceReport.byAgent`** |
| Slowest steps | none | **`TraceReport.slowest`** |
| Trace on disk | none | **`TraceFiles.save`** (one JSON file per question) |

## Concept

**Observability** is being able to answer "what happened, in what order, and what did it cost?" without adding prints. The single source is the event stream from stage 1. Three ideas:

1. **A flat list is not enough once agents run in parallel.** The orchestrator may start two `orders` specialists in the same turn. Their events interleave and carry the same agent name, so `agent` plus `depth` cannot say which `ToolFinished` belongs to which run. Each execution of an agent needs an **id** (`runId`), and a sub-agent run records the id of the run that started it (`parentRunId`). The tree is then a simple grouping by those ids.
2. **How does a sub-agent know its parent?** Through the **coroutine context**. `Agent.run` wraps its work in a `RunContext(runId, parentRunId)` element. A sub-agent is started from inside a tool call, which runs in the parent's coroutine, so it finds the parent's `RunContext` in `coroutineContext` and records its id. No parameter has to be threaded through `Tool.run`. This is structured concurrency doing useful work, and a good interview topic.
3. **Cost is a sum over events.** Tokens come from `ModelCalled`, which every model call emits: the agents' turns, the final structured answer and the reviewer. Grouping by agent name gives the cost per agent.

## What to write

**1. Contract (`AgentEvent`).** Add `runId: String = ROOT_RUN` as the **last** parameter of `AgentStarted`, `ModelCalled`, `ToolStarted`, `ToolFinished` and `GuardActed`, and `parentRunId: String? = null` to `AgentStarted`. Defaults keep the existing code and tests compiling, and `encodeDefaults` stays off, so old traces still decode. Define `const val ROOT_RUN = ""`. `ReviewRound`, `Escalated` and `Completed` do not change.

**2. Run ids and context (new, `core/agent`).**

```kotlin
fun interface RunIds { fun next(): String }
class SequentialRunIds : RunIds

class RunContext(val runId: String, val parentRunId: String?) : AbstractCoroutineContextElement(RunContext) {
    companion object Key : CoroutineContext.Key<RunContext>
}
```

`SequentialRunIds` returns `r1`, `r2`, … and **must be safe to call from concurrent coroutines**. Check which atomic counter is available in `commonMain` for both `jvm` and `wasmJs` (`kotlin.concurrent.atomics.AtomicInt` in recent Kotlin) before choosing; do not use a plain `var`.

**3. `Agent` (modify).** Add a constructor parameter `runIds: RunIds = SequentialRunIds()` (after `depth`). In `run`: take `val parent = coroutineContext[RunContext]?.runId`, create the run id, store both in fields, wrap the work in `withContext(RunContext(runId, parent))` and emit `AgentStarted(..., runId, parentRunId = parent)`. Every event the agent emits afterwards (`ModelCalled`, `ToolStarted`, `ToolFinished`, `GuardActed`, and those of `followUp` and `finish`) carries that `runId`. In the assembly, create **one** `RunIds` per question and share it among the orchestrator and all specialists, so ids are unique within a question.

**4. `ReviewLoop` (modify).** The reviewer is not an `Agent`. Its `ModelCalled` event keeps the agent name `reviewer` and uses `runId = "reviewer"`. The tree attaches events whose run has no `AgentStarted` to the root.

**5. `TraceReport` and `AgentCost` (new, `core/trace`).**

```kotlin
data class AgentCost(val agent: String, val calls: Int, val inputTokens: Int, val outputTokens: Int, val modelMs: Long)

class TraceReport {
    fun tree(events: List<AgentEvent>): String
    fun byAgent(events: List<AgentEvent>): List<AgentCost>
    fun slowest(events: List<AgentEvent>, n: Int = 3): List<AgentEvent>
}
```

- `tree`: group the events by `runId`; the run of an `AgentStarted` is a node, its children are the runs whose `parentRunId` is that id. Inside a node, list its model calls and tool calls in order; a tool call whose name is a sub-agent shows that sub-agent's node nested under it. Indent by level and print duration and tokens. Events of unknown runs hang from the root. The exact text format is yours; keep it readable in a terminal.
- `byAgent`: one `AgentCost` per agent name, from the `ModelCalled` events, in order of first appearance.
- `slowest`: the `n` events with the largest `durationMs` among `ModelCalled` and `ToolFinished`, slowest first.
- All three work on an empty list.

**6. `TraceFiles` (new, `adapters/src/jvmMain/.../trace`).**

```kotlin
class TraceFiles(private val now: () -> String = { Clock.System.now().toString() }) {
    fun save(dir: Path, title: String, events: List<AgentEvent>): Path
}
```

Creates the directory if needed and writes `trace-<timestamp>.json` (replace `:` in the timestamp) containing `{"title": ..., "events": [...]}`, pretty printed, using the `AgentEvent` serializer. Returns the path.

**7. CLI.** After each question, print the tree and the cost table (`byAgent`), and save the trace with `TraceFiles`. `traces/` is already in `.gitignore`.

## Tests (write them first)

One class per production class, MockK where there are collaborators, names `given … when … then should …`, no comments.

**`AgentTest`** (add): given an agent run when it emits events then should tag all of them with the same run id; given an agent started inside another agent's run when it emits `AgentStarted` then should record the parent run id; given a top-level agent when it starts then should have no parent run id; given two sub-agents running in parallel from one parent when they emit events then should give them distinct run ids and the same parent; given a `followUp` and a `finish` after a run when they emit then should keep the run id of the run.

**`SequentialRunIdsTest`**: ids are `r1`, `r2`, …; given many concurrent callers then should never repeat an id.

**`TraceReportTest`**: given an orchestrator that calls one specialist when building the tree then should nest the specialist under the tool call that started it; given two specialists with the same name running in parallel when building the tree then should keep their events apart; given the reviewer call when building the tree then should attach it to the root; given 2 orchestrator turns of 1000 input tokens and 1 specialist turn of 300 when summing by agent then should return 2000 and 300; given a reviewer call when summing then should include the `reviewer` agent; given events with durations when asking for the slowest then should return the top `n` slowest model or tool events; given no events then all three return empty results.

**`TraceFilesTest`**: given events when saving then should write a readable JSON file named from the timestamp inside the directory; given a directory that does not exist then should create it; given the saved file when decoding it then should give back the same events.

**`AgentEventTest`** (contract): given an event without `runId` in the JSON when decoding then should default it (old traces still load).

Also update the places that build events by hand (`AgentTest`, `ReviewLoopTest`, `CaseScorerTest`): thanks to the defaults they should keep compiling.

## Run

```bash
./gradlew test
REFERENCE_DATE=2026-10-15 ./gradlew :cli:run --args="My order 1042 has not arrived. My email is ana@example.com"
```

## What to observe

- In the printed tree, the specialists nested under the orchestrator's tool calls, and the guard actions under the call they affected.
- In the cost table, which agent spends the most (usually the orchestrator, which carries the whole conversation) and how much the reviewer costs.
- In the saved JSON, the `runId` and `parentRunId` fields that reconstruct the tree.

## Exercises (optional)

- Add a price per million tokens by model and print the cost in dollars next to the tokens.
- Print the longest chain of nested runs (the depth of delegation).
- Compare the cost of the same question with `ANTHROPIC_MODEL` set to two different models.

## How to explain it in an interview

"Every step emits an event, and the trace, the cost table and the evaluation metrics are all just functions over that list, so there is no global state. When I made specialists run in parallel, a flat event list could no longer tell which tool result belonged to which run, so I gave each agent run an id and propagated the parent through the coroutine context. The trace tree is then a grouping by those ids."
