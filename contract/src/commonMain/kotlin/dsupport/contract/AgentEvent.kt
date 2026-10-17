package dsupport.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a guard did: blocked the call, changed its arguments, or rewrote a result or answer. */
@Serializable
enum class GuardAction { DENY, MODIFY, REWRITE }

/**
 * Everything that happens while a question is answered.
 * It is the single source for traces, cost, evaluation metrics and the live screen.
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
