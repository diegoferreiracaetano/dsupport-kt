package dsupport.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvalReportTest {
    private fun result(
        id: String = "g1",
        intentOk: Boolean = true,
        escalated: Boolean = false,
        shouldEscalate: Boolean? = null,
        grounded: Boolean = true,
        factsOk: Boolean = true,
        sourcesOk: Boolean? = null,
        leakFree: Boolean = true,
        problems: List<String> = emptyList(),
        ms: Long = 1000,
        modelCalls: Int = 1,
        tokens: Int = 1000,
        error: String? = null,
        misses: List<String> = emptyList(),
    ) = CaseResult(id, intentOk, escalated, shouldEscalate, grounded, factsOk, sourcesOk, leakFree, problems, ms, modelCalls, tokens, error, misses)

    private val mixed = listOf(
        result("tp", escalated = true, shouldEscalate = true),
        result("fp", escalated = true, shouldEscalate = false),
        result("fn", escalated = false, shouldEscalate = true),
        result("tn", escalated = false, shouldEscalate = false, sourcesOk = true),
        result("invented", escalated = true, shouldEscalate = null, grounded = false, problems = listOf("ticket TCK-9999 is invented")),
    )

    @Test
    fun `given one true positive one false positive and one false negative when reporting then should compute precision and recall of 50 percent`() {
        val report = EvalReport(mixed)

        assertEquals(0.5, report.escalationPrecision)
        assertEquals(0.5, report.escalationRecall)
    }

    @Test
    fun `given cases without an escalation expectation when reporting then should leave them out of precision and recall`() {
        val report = EvalReport(listOf(result(escalated = true, shouldEscalate = null)))

        assertNull(report.escalationPrecision)
        assertNull(report.escalationRecall)
    }

    @Test
    fun `given one ungrounded case out of five when reporting then should compute groundedness of 80 percent`() {
        assertEquals(0.8, EvalReport(mixed).grounded)
    }

    @Test
    fun `given a single case that expects sources when reporting then should compute the source rate only over that case`() {
        assertEquals(1.0, EvalReport(mixed).sources)
    }

    @Test
    fun `given wrong routing in two of five cases when reporting then should compute routing accuracy of 60 percent`() {
        val results = List(5) { result("c$it", intentOk = it >= 2) }

        assertEquals(0.6, EvalReport(results).routing)
    }

    @Test
    fun `given a leak in one of four cases when reporting then should compute the leak free rate of 75 percent`() {
        val results = List(4) { result("c$it", leakFree = it != 0) }

        assertEquals(0.75, EvalReport(results).leakFree)
    }

    @Test
    fun `given several cases when reporting then should average latency calls and tokens`() {
        val report = EvalReport(listOf(result(ms = 1000, modelCalls = 2, tokens = 1000), result(ms = 3000, modelCalls = 4, tokens = 3000)))

        assertEquals(2000L, report.averageLatencyMs)
        assertEquals(3.0, report.averageModelCalls)
        assertEquals(2000L, report.averageTokens)
    }

    @Test
    fun `given a mixed set when rendering the markdown then should show the title the metrics and a row per case`() {
        val markdown = EvalReport(mixed).toMarkdown("Evaluation 2026-10-15")

        assertTrue(markdown.startsWith("# Evaluation 2026-10-15"))
        assertTrue("| Escalation precision | 50% |" in markdown)
        assertTrue("| Groundedness (code checks) | 80% |" in markdown)
        assertTrue("| invented |" in markdown && "ticket TCK-9999 is invented" in markdown)
        assertTrue("5 cases, 0 with an execution error." in markdown)
    }

    @Test
    fun `given a case with misses when rendering the markdown then should explain it under cases to look at`() {
        val failing = result(
            "g12", escalated = true, shouldEscalate = false, factsOk = false,
            misses = listOf("missing fact: 'email'", "escalated=true but expected false"),
        ).copy(question = "Where is my order 1042?", intent = "order", answer = "I opened a ticket for you.")

        val markdown = EvalReport(listOf(failing, result("g01"))).toMarkdown("Report")

        assertTrue("## Cases to look at" in markdown)
        assertTrue("### g12" in markdown)
        assertTrue("- Question: Where is my order 1042?" in markdown)
        assertTrue("- Answer: I opened a ticket for you." in markdown)
        assertTrue("- Misses: missing fact: 'email'; escalated=true but expected false" in markdown)
        assertFalse("### g01" in markdown)
    }

    @Test
    fun `given only passing cases when rendering the markdown then should not add the cases to look at section`() {
        assertFalse("Cases to look at" in EvalReport(listOf(result("g01"))).toMarkdown("Report"))
    }

    @Test
    fun `given a case with misses when rendering the case table then should show them in the notes`() {
        val markdown = EvalReport(listOf(result("g12", misses = listOf("missing fact: 'email'")))).toMarkdown("Report")

        assertTrue("| g12 |" in markdown && "missing fact: 'email' |" in markdown)
    }

    @Test
    fun `given a metric with no data when rendering the markdown then should show n slash a`() {
        val markdown = EvalReport(listOf(result(escalated = true, shouldEscalate = null))).toMarkdown("Report")

        assertTrue("| Escalation precision | n/a |" in markdown)
    }

    @Test
    fun `given a case that failed to run when rendering the markdown then should count it and show the error`() {
        val markdown = EvalReport(listOf(result("boom", error = "API responded 500"))).toMarkdown("Report")

        assertTrue("1 case, 1 with an execution error." in markdown)
        assertTrue("API responded 500" in markdown)
    }
}
