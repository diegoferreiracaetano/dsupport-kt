package dsupport.eval

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaseScorerTest {
    private val scorer = CaseScorer()
    private val evidence = listOf("""{"ok":true,"ticket_id":"TCK-1001"}""", "Returns Policy: 30 days.")

    private fun case(
        intents: List<String> = listOf("order"),
        shouldEscalate: Boolean? = null,
        mustContain: List<String> = emptyList(),
        mustNotContain: List<String> = emptyList(),
        sources: List<String> = emptyList(),
    ) = GoldenCase("g1", "question", intents, shouldEscalate, mustContain, mustNotContain, sources)

    private fun answer(
        text: String = "Ticket TCK-1001 was opened.",
        intent: String = "order",
        sources: List<String> = emptyList(),
        escalated: Boolean = true,
        ticket: String? = "TCK-1001",
    ) = SupportAnswer(text, intent, sources, 0.9, escalated, ticket)

    private fun score(case: GoldenCase, answer: SupportAnswer, events: List<AgentEvent> = emptyList(), elapsedMs: Long = 0) =
        scorer.score(case, answer, evidence, events, elapsedMs)

    @Test
    fun `given an intent among the expected ones when scoring then should mark the routing as correct`() {
        assertTrue(score(case(intents = listOf("order", "complaint")), answer(intent = "complaint")).intentOk)
    }

    @Test
    fun `given an intent outside the expected ones when scoring then should mark the routing as wrong`() {
        assertFalse(score(case(intents = listOf("policy")), answer(intent = "order")).intentOk)
    }

    @Test
    fun `given every expected fact in the text ignoring case when scoring then should mark the facts as present`() {
        val result = score(case(mustContain = listOf("30 DAYS", "business")), answer(text = "Returns take 30 days, in business terms. TCK-1001"))

        assertTrue(result.factsOk)
    }

    @Test
    fun `given a missing expected fact when scoring then should mark the facts as missing`() {
        assertFalse(score(case(mustContain = listOf("30 days")), answer(text = "Soon. TCK-1001")).factsOk)
    }

    @Test
    fun `given the expected sources cited when scoring then should mark the sources as correct`() {
        val result = score(case(sources = listOf("returns policy")), answer(sources = listOf("Returns Policy")))

        assertEquals(true, result.sourcesOk)
    }

    @Test
    fun `given a missing expected source when scoring then should mark the sources as wrong`() {
        assertEquals(false, score(case(sources = listOf("Warranty")), answer(sources = listOf("Returns Policy"))).sourcesOk)
    }

    @Test
    fun `given a case that expects no sources when scoring then should leave the sources unjudged`() {
        assertNull(score(case(), answer()).sourcesOk)
    }

    @Test
    fun `given forbidden content in the text when scoring then should mark it as a leak`() {
        val result = score(case(mustNotContain = listOf("FS104200")), answer(text = "Your tracking is fs104200. TCK-1001"))

        assertFalse(result.leakFree)
    }

    @Test
    fun `given no forbidden content in the text when scoring then should mark it as leak free`() {
        assertTrue(score(case(mustNotContain = listOf("FS104200")), answer()).leakFree)
    }

    @Test
    fun `given an invented ticket when scoring then should mark the answer as ungrounded and list the problems`() {
        val result = score(case(), answer(text = "Ticket TCK-9999 was opened.", ticket = "TCK-9999"))

        assertFalse(result.grounded)
        assertTrue(result.problems.any { "TCK-9999" in it })
    }

    @Test
    fun `given model call events when scoring then should count the calls and sum the tokens`() {
        val events = listOf(
            AgentEvent.ModelCalled("orchestrator", 900, 100, 400),
            AgentEvent.ToolStarted("orchestrator", "orders", "{}"),
            AgentEvent.ModelCalled("reviewer", 300, 20, 200),
        )

        val result = score(case(), answer(), events, elapsedMs = 1500)

        assertEquals(2, result.modelCalls)
        assertEquals(1320, result.tokens)
        assertEquals(1500, result.ms)
    }

    @Test
    fun `given the expectation and the escalation flag when scoring then should copy both to the result`() {
        val result = score(case(shouldEscalate = false), answer(escalated = true))

        assertTrue(result.escalated)
        assertEquals(false, result.shouldEscalate)
    }

    @Test
    fun `given an intent outside the expected ones when scoring then should list it among the misses`() {
        val result = score(case(intents = listOf("policy")), answer(intent = "order"))

        assertTrue(result.misses.any { "intent" in it && "order" in it && "policy" in it })
    }

    @Test
    fun `given an escalation that contradicts the expectation when scoring then should list it among the misses`() {
        val result = score(case(shouldEscalate = false), answer(escalated = true))

        assertTrue(result.misses.any { "escalated=true" in it && "expected false" in it })
    }

    @Test
    fun `given an unjudged escalation when scoring then should not list it among the misses`() {
        assertTrue(score(case(shouldEscalate = null), answer(escalated = true)).misses.isEmpty())
    }

    @Test
    fun `given missing facts and sources when scoring then should name each one among the misses`() {
        val result = score(
            case(mustContain = listOf("30 days", "email"), sources = listOf("Warranty")),
            answer(text = "Soon. TCK-1001", sources = listOf("Returns Policy")),
        )

        assertTrue(result.misses.contains("missing fact: '30 days'"))
        assertTrue(result.misses.contains("missing fact: 'email'"))
        assertTrue(result.misses.contains("missing source: 'Warranty'"))
    }

    @Test
    fun `given forbidden content when scoring then should name it among the misses`() {
        val result = score(case(mustNotContain = listOf("FS104200")), answer(text = "Tracking FS104200. TCK-1001"))

        assertTrue(result.misses.contains("forbidden content found: 'FS104200'"))
    }

    @Test
    fun `given review rounds in the events when scoring then should keep the trail with the problems of each round`() {
        val events = listOf(
            AgentEvent.ReviewRound(1, false, listOf("confidence 0.3 without escalating", "ticket missing")),
            AgentEvent.ReviewRound(2, true, emptyList()),
        )

        val result = score(case(), answer(), events)

        assertEquals(
            listOf("round 1: rejected (confidence 0.3 without escalating; ticket missing)", "round 2: approved"),
            result.reviewTrail,
        )
    }

    @Test
    fun `given a perfect answer when scoring then should have no misses`() {
        assertEquals(emptyList(), score(case(), answer()).misses)
    }

    @Test
    fun `given the question and the answer when scoring then should keep them and cut a long answer`() {
        val long = "word ".repeat(200) + "TCK-1001"

        val result = score(case(), answer(text = long))

        assertEquals("question", result.question)
        assertEquals("order", result.intent)
        assertTrue(result.answer.length <= 403 && result.answer.endsWith("..."))
    }

    @Test
    fun `given a case that failed to run when recording it then should fail every criterion and keep the error`() {
        val result = scorer.failed(case(sources = listOf("Warranty"), shouldEscalate = true), "API responded 500")

        assertFalse(result.intentOk)
        assertFalse(result.grounded)
        assertFalse(result.factsOk)
        assertEquals(false, result.sourcesOk)
        assertTrue(result.leakFree)
        assertEquals("API responded 500", result.error)
        assertEquals(true, result.shouldEscalate)
        assertEquals("question", result.question)
        assertTrue(result.misses.any { "API responded 500" in it })
    }
}
