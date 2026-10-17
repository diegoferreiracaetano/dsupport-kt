package dsupport.eval

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer

/** Everything the scoring needs to know about one execution of a case. */
data class CaseRun(
    val answer: SupportAnswer,
    val evidence: List<String>,
    val events: List<AgentEvent>,
    val elapsedMs: Long,
)

fun interface CaseAnswerer {
    suspend fun answer(case: GoldenCase): CaseRun
}
