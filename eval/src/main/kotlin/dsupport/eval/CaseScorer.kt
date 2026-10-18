package dsupport.eval

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import dsupport.core.answer.SupportAnswerValidator

private const val EXCERPT_LIMIT = 400

/** Scores one case with plain code, no model involved. */
class CaseScorer(private val validator: SupportAnswerValidator = SupportAnswerValidator()) {

    fun score(case: GoldenCase, answer: SupportAnswer, evidence: List<String>, events: List<AgentEvent>, elapsedMs: Long): CaseResult {
        val problems = validator.problems(answer, evidence)
        val modelCalls = events.filterIsInstance<AgentEvent.ModelCalled>()
        val misses = missesOf(case, answer)
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
            misses = misses,
            question = case.question,
            intent = answer.intent,
            answer = excerpt(answer.answer),
            reviewTrail = reviewTrailOf(events),
            codeEscalated = events.any { it is AgentEvent.Escalated },
        )
    }

    private fun missesOf(case: GoldenCase, answer: SupportAnswer): List<String> = buildList {
        if (answer.intent !in case.expectedIntents) add("intent '${answer.intent}' is not one of ${case.expectedIntents}")
        if (case.shouldEscalate != null && answer.escalated != case.shouldEscalate) {
            add("escalated=${answer.escalated} but expected ${case.shouldEscalate}")
        }
        case.mustContain.filterNot { answer.answer.contains(it, ignoreCase = true) }.forEach { add("missing fact: '$it'") }
        case.expectedSources.filterNot { expected -> answer.sources.any { it.contains(expected, ignoreCase = true) } }
            .forEach { add("missing source: '$it'") }
        case.mustNotContain.filter { answer.answer.contains(it, ignoreCase = true) }.forEach { add("forbidden content found: '$it'") }
    }

    private fun reviewTrailOf(events: List<AgentEvent>): List<String> =
        events.filterIsInstance<AgentEvent.ReviewRound>().map { round ->
            if (round.approved) "round ${round.round}: approved"
            else "round ${round.round}: rejected (${round.problems.joinToString("; ")})"
        }

    private fun excerpt(text: String): String = if (text.length <= EXCERPT_LIMIT) text else text.take(EXCERPT_LIMIT) + "..."

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
        misses = listOf("the run failed: $error"),
        question = case.question,
    )
}
