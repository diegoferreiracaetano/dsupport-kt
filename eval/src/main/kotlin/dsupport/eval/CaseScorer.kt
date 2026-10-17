package dsupport.eval

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import dsupport.core.answer.SupportAnswerValidator

/** Scores one case with plain code, no model involved. */
class CaseScorer(private val validator: SupportAnswerValidator = SupportAnswerValidator()) {

    fun score(case: GoldenCase, answer: SupportAnswer, evidence: List<String>, events: List<AgentEvent>, elapsedMs: Long): CaseResult {
        val problems = validator.problems(answer, evidence)
        val modelCalls = events.filterIsInstance<AgentEvent.ModelCalled>()
        return CaseResult(
            id = case.id,
            intentOk = answer.intent in case.expectedIntents,
            escalated = answer.escalated,
            shouldEscalate = case.shouldEscalate,
            grounded = problems.isEmpty(),
            factsOk = case.mustContain.all { answer.answer.contains(it, ignoreCase = true) },
            sourcesOk = if (case.expectedSources.isEmpty()) null else case.expectedSources.all { expected ->
                answer.sources.any { it.contains(expected, ignoreCase = true) }
            },
            leakFree = case.mustNotContain.none { answer.answer.contains(it, ignoreCase = true) },
            problems = problems,
            ms = elapsedMs,
            modelCalls = modelCalls.size,
            tokens = modelCalls.sumOf { it.inputTokens + it.outputTokens },
        )
    }

    /** A case that crashed (an API error, for instance) fails every criterion but does not stop the evaluation. */
    fun failed(case: GoldenCase, error: String): CaseResult = CaseResult(
        id = case.id,
        intentOk = false,
        escalated = false,
        shouldEscalate = case.shouldEscalate,
        grounded = false,
        factsOk = false,
        sourcesOk = if (case.expectedSources.isEmpty()) null else false,
        leakFree = true,
        problems = emptyList(),
        ms = 0,
        modelCalls = 0,
        tokens = 0,
        error = error,
    )
}
