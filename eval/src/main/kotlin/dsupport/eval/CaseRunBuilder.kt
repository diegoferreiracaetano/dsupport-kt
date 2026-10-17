package dsupport.eval

import dsupport.cli.ORCHESTRATOR_NAME
import dsupport.contract.AgentEvent

class CaseRunBuilder {

    /**
     * The evidence is what the orchestrator received from its specialists, plus the tickets file of the case:
     * a ticket opened by code after the review limit is backed by that file, not by a tool result.
     */
    fun build(events: List<AgentEvent>, ticketsFile: String, elapsedMs: Long): CaseRun {
        val answer = events.filterIsInstance<AgentEvent.Completed>().lastOrNull()?.answer
            ?: throw IllegalStateException("the run finished without an answer")
        val received = events.filterIsInstance<AgentEvent.ToolFinished>()
            .filter { it.agent == ORCHESTRATOR_NAME }
            .map { it.result }
        return CaseRun(answer, received + ticketsFile, events, elapsedMs)
    }
}
