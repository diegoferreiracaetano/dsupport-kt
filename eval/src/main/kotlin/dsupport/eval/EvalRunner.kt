package dsupport.eval

import kotlinx.coroutines.CancellationException

/** Runs the golden cases one by one. A case that crashes fails alone and never stops the evaluation. */
class EvalRunner(
    private val answerer: CaseAnswerer,
    private val scorer: CaseScorer = CaseScorer(),
    private val progress: (GoldenCase, CaseResult) -> Unit = { _, _ -> },
) {

    /** With [repeat] above 1 every case runs that many times, and the attempts are numbered (`g04#1`, `g04#2`, ...). */
    suspend fun run(cases: List<GoldenCase>, only: Set<String> = emptySet(), repeat: Int = 1): List<CaseResult> =
        cases.filter { only.isEmpty() || it.id in only }.flatMap { case ->
            (1..repeat).map { attempt ->
                val result = attemptOf(case)
                val numbered = if (repeat > 1) result.copy(id = "${case.id}#$attempt") else result
                progress(case, numbered)
                numbered
            }
        }

    private suspend fun attemptOf(case: GoldenCase): CaseResult = try {
        val run = answerer.answer(case)
        scorer.score(case, run.answer, run.evidence, run.events, run.elapsedMs)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        scorer.failed(case, e.message ?: e::class.simpleName.orEmpty())
    }
}
