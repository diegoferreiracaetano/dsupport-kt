package dsupport.core.agent

import dsupport.contract.AgentEvent
import dsupport.contract.GuardAction
import dsupport.contract.ROOT_RUN
import dsupport.core.guard.Decision
import dsupport.core.guard.Hook
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

/**
 * The agentic loop: the model decides, the code executes. The model never runs anything, it only asks.
 * The loop is always bounded by [maxTurns].
 */
class Agent(
    val name: String,
    private val llm: LlmClient,
    private val system: String,
    tools: List<Tool>,
    private val hooks: List<Hook> = emptyList(),
    private val events: EventSink = EventSink.None,
    private val maxTurns: Int = 5,
    private val depth: Int = 0,
    private val runIds: RunIds = SequentialRunIds(),
) {
    private val toolsByName = tools.associateBy { it.spec.name }
    private val specs = tools.map { it.spec }
    private var history = mutableListOf<Message>()
    private var runId = ROOT_RUN
    private var parentRunId: String? = null

    /** What the tools returned in this conversation, used to check the final answer. */
    val evidence: List<String>
        get() = history.filterIsInstance<Message.ToolResult>().map { it.content }

    suspend fun run(task: String): String {
        history = mutableListOf(Message.User(task))
        parentRunId = coroutineContext[RunContext]?.runId
        runId = runIds.next()
        return inRun {
            events.emit(AgentEvent.AgentStarted(name, depth, task, runId, parentRunId))
            loop()
        }
    }

    suspend fun followUp(message: String): String {
        history += Message.User(message)
        return inRun { loop() }
    }

    private suspend fun <T> inRun(block: suspend () -> T): T = withContext(RunContext(runId, parentRunId)) { block() }

    suspend fun finish(schema: OutputSchema): JsonObject {
        val closing = Message.User("Now return your final answer in the ${schema.name} format.")
        val mark = TimeSource.Monotonic.markNow()
        val reply = llm.structured(LlmRequest(system, history + closing, specs), schema)
        events.emit(
            AgentEvent.ModelCalled(
                name, reply.usage.inputTokens, reply.usage.outputTokens, mark.elapsedNow().inWholeMilliseconds, runId,
            ),
        )
        return reply.value
    }

    private suspend fun loop(): String {
        repeat(maxTurns) {
            val mark = TimeSource.Monotonic.markNow()
            val reply = llm.next(LlmRequest(system, history.toList(), specs))
            events.emit(
                AgentEvent.ModelCalled(
                    name, reply.usage.inputTokens, reply.usage.outputTokens, mark.elapsedNow().inWholeMilliseconds, runId,
                ),
            )
            when (reply) {
                is LlmReply.Text -> {
                    val text = finalAnswer(reply.text)
                    history += Message.AssistantText(text)
                    return text
                }
                is LlmReply.ToolCalls -> {
                    history += Message.AssistantToolCalls(reply.calls)
                    val results = coroutineScope {
                        reply.calls.map { call -> async { execute(call.tool, call.args) } }.awaitAll()
                    }
                    reply.calls.zip(results).forEach { (call, result) ->
                        history += Message.ToolResult(call.id, call.tool, result.content, result.isError)
                    }
                }
            }
        }
        return "Turn limit of $maxTurns reached. Escalating to a human agent."
    }

    private suspend fun finalAnswer(proposed: String): String = hooks.fold(proposed) { current, hook ->
        val next = hook.beforeFinal(name, history.toList(), current)
        if (next != current) {
            events.emit(AgentEvent.GuardActed(name, hook.name, null, GuardAction.REWRITE, "final answer rewritten", runId))
        }
        next
    }

    private suspend fun execute(toolName: String, args: JsonObject): ToolResult {
        events.emit(AgentEvent.ToolStarted(name, toolName, args.toString(), runId))
        val mark = TimeSource.Monotonic.markNow()
        val tool = toolsByName[toolName]
        val result = if (tool == null) {
            ToolResult("error: tool '$toolName' does not exist", isError = true)
        } else {
            try {
                guarded(tool, args)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolResult("error running '$toolName': ${e.message}", isError = true)
            }
        }
        events.emit(
            AgentEvent.ToolFinished(name, toolName, result.content, result.isError, mark.elapsedNow().inWholeMilliseconds, runId),
        )
        return result
    }

    private suspend fun guarded(tool: Tool, requested: JsonObject): ToolResult {
        var args = requested
        for (hook in hooks) {
            when (val decision = hook.beforeTool(name, tool.spec, args)) {
                Decision.Allow -> Unit
                is Decision.Deny -> {
                    events.emit(AgentEvent.GuardActed(name, hook.name, tool.spec.name, GuardAction.DENY, decision.reason, runId))
                    return ToolResult("blocked by ${hook.name}: ${decision.reason}", isError = true)
                }
                is Decision.Modify -> {
                    events.emit(AgentEvent.GuardActed(name, hook.name, tool.spec.name, GuardAction.MODIFY, "arguments changed", runId))
                    args = decision.args
                }
            }
        }
        val raw = try {
            tool.run(args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult("error running '${tool.spec.name}': ${e.message}", isError = true)
        }
        return hooks.fold(raw) { current, hook ->
            val next = hook.afterTool(name, tool.spec, args, current)
            if (next != current) {
                events.emit(AgentEvent.GuardActed(name, hook.name, tool.spec.name, GuardAction.REWRITE, "result rewritten", runId))
            }
            next
        }
    }
}
