package dsupport.eval

import java.util.Locale

/** The metrics of a set of results. Each one answers a different question about the system. */
class EvalReport(private val results: List<CaseResult>) {

    val routing: Double? = rate(results) { it.intentOk }
    val grounded: Double? = rate(results) { it.grounded }
    val facts: Double? = rate(results) { it.factsOk }
    val sources: Double? = rate(results.filter { it.sourcesOk != null }) { it.sourcesOk == true }
    val leakFree: Double? = rate(results) { it.leakFree }

    private val judged = results.filter { it.shouldEscalate != null }
    private val truePositives = judged.count { it.escalated && it.shouldEscalate == true }
    private val falsePositives = judged.count { it.escalated && it.shouldEscalate == false }
    private val falseNegatives = judged.count { !it.escalated && it.shouldEscalate == true }

    val escalationPrecision: Double? =
        if (truePositives + falsePositives == 0) null else truePositives.toDouble() / (truePositives + falsePositives)
    val escalationRecall: Double? =
        if (truePositives + falseNegatives == 0) null else truePositives.toDouble() / (truePositives + falseNegatives)

    val averageLatencyMs: Long = results.map { it.ms }.average().takeIf { !it.isNaN() }?.toLong() ?: 0
    val averageModelCalls: Double = results.map { it.modelCalls }.average().takeIf { !it.isNaN() } ?: 0.0
    val averageTokens: Long = results.map { it.tokens }.average().takeIf { !it.isNaN() }?.toLong() ?: 0

    fun toMarkdown(title: String): String = buildString {
        val errors = results.count { it.error != null }
        appendLine("# $title")
        appendLine()
        appendLine("${results.size} ${if (results.size == 1) "case" else "cases"}, $errors with an execution error.")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("| --- | --- |")
        appendLine("| Routing accuracy (intent) | ${percent(routing)} |")
        appendLine("| Groundedness (code checks) | ${percent(grounded)} |")
        appendLine("| Expected facts present | ${percent(facts)} |")
        appendLine("| Expected sources cited | ${percent(sources)} |")
        appendLine("| No forbidden content (leaks) | ${percent(leakFree)} |")
        appendLine("| Escalation precision | ${percent(escalationPrecision)} |")
        appendLine("| Escalation recall | ${percent(escalationRecall)} |")
        appendLine("| Average latency | $averageLatencyMs ms |")
        appendLine("| Model calls per case | ${String.format(Locale.US, "%.1f", averageModelCalls)} |")
        appendLine("| Tokens per case | $averageTokens |")
        appendLine()
        appendLine("| Case | Intent | Escalated (expected) | Grounded | Facts | Sources | No leak | ms | Calls | Notes |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        results.forEach { result ->
            val expected = result.shouldEscalate?.let { if (it) "yes" else "no" } ?: "-"
            val notes = result.error ?: (result.misses + result.problems).joinToString("; ")
            appendLine(
                "| ${result.id} | ${mark(result.intentOk)} | ${if (result.escalated) "yes" else "no"} ($expected) | " +
                    "${mark(result.grounded)} | ${mark(result.factsOk)} | ${mark(result.sourcesOk)} | " +
                    "${mark(result.leakFree)} | ${result.ms} | ${result.modelCalls} | $notes |",
            )
        }
        appendCasesToLookAt()
    }

    private fun StringBuilder.appendCasesToLookAt() {
        val flagged = results.filter { it.error != null || it.misses.isNotEmpty() || it.problems.isNotEmpty() }
        if (flagged.isEmpty()) return
        appendLine()
        appendLine("## Cases to look at")
        flagged.forEach { result ->
            appendLine()
            appendLine("### ${result.id}")
            appendLine("- Question: ${result.question}")
            appendLine("- Intent: ${result.intent}")
            appendLine("- Answer: ${result.answer}")
            appendLine("- Misses: ${result.misses.joinToString("; ")}")
            if (result.reviewTrail.isNotEmpty()) appendLine("- Review: ${result.reviewTrail.joinToString("; ")}")
            if (result.problems.isNotEmpty()) appendLine("- Problems: ${result.problems.joinToString("; ")}")
            result.error?.let { appendLine("- Error: $it") }
        }
    }

    private fun rate(list: List<CaseResult>, accepted: (CaseResult) -> Boolean): Double? =
        if (list.isEmpty()) null else list.count(accepted).toDouble() / list.size

    private fun percent(value: Double?): String =
        value?.let { String.format(Locale.US, "%.0f%%", it * 100) } ?: "n/a"

    private fun mark(value: Boolean?): String = when (value) {
        true -> "yes"
        false -> "NO"
        null -> "-"
    }
}
