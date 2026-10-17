package dsupport.eval

import kotlinx.coroutines.CancellationException

/** Runs the golden cases one by one. A case that crashes fails alone and never stops the evaluation. */
class EvalRunner(
    private val answerer: CaseAnswerer,
    private val scorer: CaseScorer = CaseScorer(),
    private val progress: (GoldenCase, CaseResult) -> Unit = { _, _ -> },
) {

    suspend fun run(cases: List<GoldenCase>, only: Set<String> = emptySet()): List<CaseResult> =
        cases.filter { only.isEmpty() || it.id in only }.map { case ->
            val result = try {
                val run = answerer.answer(case)
                scorer.score(case, run.answer, run.evidence, run.events, run.elapsedMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scorer.failed(case, e.message ?: e::class.simpleName.orEmpty())
            }
            progress(case, result)
            result
        }
}
