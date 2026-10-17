package dsupport.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** O que uma guarda fez: bloqueou a chamada, alterou os argumentos ou reescreveu um resultado/resposta. */
@Serializable
enum class GuardAction { DENY, MODIFY, REWRITE }

/**
 * Tudo que acontece durante uma pergunta. É a única fonte de trace, custo, métricas de avaliação
 * e da tela ao vivo: nenhum estado global.
 */
@Serializable
sealed interface AgentEvent {

    @Serializable @SerialName("agent_started")
    data class AgentStarted(val agent: String, val depth: Int, val task: String) : AgentEvent

    @Serializable @SerialName("model_called")
    data class ModelCalled(val agent: String, val inputTokens: Int, val outputTokens: Int, val durationMs: Long) : AgentEvent

    @Serializable @SerialName("tool_started")
    data class ToolStarted(val agent: String, val tool: String, val args: String) : AgentEvent

    @Serializable @SerialName("tool_finished")
    data class ToolFinished(
        val agent: String,
        val tool: String,
        val result: String,
        val isError: Boolean,
        val durationMs: Long,
    ) : AgentEvent

    @Serializable @SerialName("guard_acted")
    data class GuardActed(
        val agent: String,
        val guard: String,
        val tool: String?,
        val action: GuardAction,
        val detail: String,
    ) : AgentEvent

    @Serializable @SerialName("review_round")
    data class ReviewRound(val round: Int, val approved: Boolean, val problems: List<String>) : AgentEvent

    @Serializable @SerialName("escalated")
    data class Escalated(val ticketId: String, val reason: String) : AgentEvent

    @Serializable @SerialName("completed")
    data class Completed(val answer: SupportAnswer) : AgentEvent
}
