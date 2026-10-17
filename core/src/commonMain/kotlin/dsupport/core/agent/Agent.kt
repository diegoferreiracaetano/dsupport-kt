package dsupport.core.agent

import dsupport.contract.AgentEvent
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
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
    private val events: EventSink = EventSink.None,
    private val maxTurns: Int = 5,
    private val depth: Int = 0,
) {
    private val toolsByName = tools.associateBy { it.spec.name }
    private val specs = tools.map { it.spec }
    private var history = mutableListOf<Message>()

    /** What the tools returned in this conversation, used to check the final answer. */
    val evidence: List<String>
        get() = history.filterIsInstance<Message.ToolResult>().map { it.content }

    suspend fun run(task: String): String {
        history = mutableListOf(Message.User(task))
        events.emit(AgentEvent.AgentStarted(name, depth, task))
        return loop()
    }

    suspend fun followUp(message: String): String {
        history += Message.User(message)
        return loop()
    }

    suspend fun finish(schema: OutputSchema): JsonObject {
        val closing = Message.User("Now return your final answer in the ${schema.name} format.")
        return llm.structured(LlmRequest(system, history + closing, specs), schema)
    }

    private suspend fun loop(): String {
        repeat(maxTurns) {
            val mark = TimeSource.Monotonic.markNow()
            val reply = llm.next(LlmRequest(system, history.toList(), specs))
            events.emit(
                AgentEvent.ModelCalled(
                    name, reply.usage.inputTokens, reply.usage.outputTokens, mark.elapsedNow().inWholeMilliseconds,
                ),
            )
            when (reply) {
                is LlmReply.Text -> {
                    history += Message.AssistantText(reply.text)
                    return reply.text
                }
                is LlmReply.ToolCalls -> {
                    history += Message.AssistantToolCalls(reply.calls)
                    for (call in reply.calls) {
                        val result = execute(call.tool, call.args)
                        history += Message.ToolResult(call.id, call.tool, result.content, result.isError)
                    }
                }
            }
        }
        return "Turn limit of $maxTurns reached. Escalating to a human agent."
    }

    private suspend fun execute(toolName: String, args: JsonObject): ToolResult {
        events.emit(AgentEvent.ToolStarted(name, toolName, args.toString()))
        val mark = TimeSource.Monotonic.markNow()
        val result = try {
            toolsByName[toolName]?.run(args) ?: ToolResult("error: tool '$toolName' does not exist", isError = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult("error running '$toolName': ${e.message}", isError = true)
        }
        events.emit(
            AgentEvent.ToolFinished(name, toolName, result.content, result.isError, mark.elapsedNow().inWholeMilliseconds),
        )
        return result
    }
}
