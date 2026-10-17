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
 * O loop agêntico: o modelo decide, o código executa. O modelo nunca executa nada, só pede.
 * Sempre com limite de turnos.
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

    /** O que as ferramentas devolveram nesta conversa: a base para conferir a resposta final. */
    val evidence: List<String>
        get() = history.filterIsInstance<Message.ToolResult>().map { it.content }

    /** Começa uma conversa nova. */
    suspend fun run(task: String): String {
        history = mutableListOf(Message.User(task))
        events.emit(AgentEvent.AgentStarted(name, depth, task))
        return loop()
    }

    /** Continua a MESMA conversa (o revisor pede ajuste sem refazer as consultas). */
    suspend fun followUp(message: String): String {
        history += Message.User(message)
        return loop()
    }

    /** Pede a resposta final no formato do schema, com a conversa inteira como contexto. */
    suspend fun finish(schema: OutputSchema): JsonObject {
        val closing = Message.User("Devolva agora a sua resposta final no formato ${schema.name}.")
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
                    // O histórico guarda o pedido original do modelo: o id tem que bater com o resultado.
                    history += Message.AssistantToolCalls(reply.calls)
                    for (call in reply.calls) {
                        val result = execute(call.tool, call.args)
                        history += Message.ToolResult(call.id, call.tool, result.content, result.isError)
                    }
                }
            }
        }
        return "Limite de $maxTurns turnos atingido. Encaminhando a um atendente."
    }

    private suspend fun execute(toolName: String, args: JsonObject): ToolResult {
        events.emit(AgentEvent.ToolStarted(name, toolName, args.toString()))
        val mark = TimeSource.Monotonic.markNow()
        val result = try {
            toolsByName[toolName]?.run(args) ?: ToolResult("erro: ferramenta '$toolName' não existe", isError = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult("erro ao executar '$toolName': ${e.message}", isError = true)
        }
        events.emit(
            AgentEvent.ToolFinished(name, toolName, result.content, result.isError, mark.elapsedNow().inWholeMilliseconds),
        )
        return result
    }
}
