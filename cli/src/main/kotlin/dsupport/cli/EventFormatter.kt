package dsupport.cli

import dsupport.contract.AgentEvent
import dsupport.core.trace.AgentCost

private const val TEXT_LIMIT = 80
private const val RESULT_LIMIT = 100
private const val NAME_WIDTH = 12

/** One readable line per event, for the terminal. */
class EventFormatter {

    fun format(event: AgentEvent): String = when (event) {
        is AgentEvent.AgentStarted -> "[${event.agent}] started: ${cut(event.task, TEXT_LIMIT)}"
        is AgentEvent.ModelCalled ->
            "[${event.agent}] model call: ${event.inputTokens} in / ${event.outputTokens} out tokens, ${event.durationMs} ms"
        is AgentEvent.ToolStarted -> "[${event.agent}] calls ${event.tool} ${cut(event.args, RESULT_LIMIT)}"
        is AgentEvent.ToolFinished -> {
            val outcome = if (event.isError) "ERROR" else "finished"
            "[${event.agent}] ${event.tool} $outcome in ${event.durationMs} ms: ${cut(event.result, RESULT_LIMIT)}"
        }
        is AgentEvent.GuardActed -> {
            val target = event.tool?.let { "on $it" } ?: "on the answer"
            "[${event.agent}] guard ${event.guard} ${event.action} $target: ${event.detail}"
        }
        is AgentEvent.ReviewRound ->
            if (event.approved) "review round ${event.round}: approved"
            else "review round ${event.round}: rejected (${event.problems.joinToString("; ")})"
        is AgentEvent.Escalated -> "escalated to ticket ${event.ticketId}: ${event.reason}"
        is AgentEvent.Completed -> "completed"
    }

    fun formatCost(costs: List<AgentCost>): String {
        val rows = costs + AgentCost("total", costs.sumOf { it.calls }, costs.sumOf { it.inputTokens }, costs.sumOf { it.outputTokens }, costs.sumOf { it.modelMs })
        val header = "agent".padEnd(NAME_WIDTH) + "calls".padStart(7) + "in tokens".padStart(11) + "out tokens".padStart(12) + "model ms".padStart(10)
        val lines = rows.map {
            it.agent.padEnd(NAME_WIDTH) + it.calls.toString().padStart(7) + it.inputTokens.toString().padStart(11) +
                it.outputTokens.toString().padStart(12) + it.modelMs.toString().padStart(10)
        }
        return (listOf(header) + lines).joinToString("\n")
    }

    private fun cut(text: String, limit: Int): String =
        if (text.length <= limit) text else text.take(limit) + "..."
}
