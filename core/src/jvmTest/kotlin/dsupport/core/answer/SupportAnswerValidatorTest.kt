package dsupport.core.answer

import dsupport.contract.SupportAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportAnswerValidatorTest {
    private val validator = SupportAnswerValidator()
    private val evidence = listOf(
        """{"ok":true,"ticket_id":"TCK-1001"}""",
        """{"ok":true,"results":[{"title":"Returns Policy","snippet":"30 days"}]}""",
    )

    private fun answer(
        text: String = "Your order is late, ticket TCK-1001 was opened.",
        intent: String = "order",
        sources: List<String> = emptyList(),
        confidence: Double = 0.9,
        escalated: Boolean = true,
        ticketId: String? = "TCK-1001",
    ) = SupportAnswer(text, intent, sources, confidence, escalated, ticketId)

    private fun problems(answer: SupportAnswer, evidence: List<String> = this.evidence) = validator.problems(answer, evidence)

    @Test
    fun `given a grounded answer when validating then should report no problems`() {
        assertEquals(emptyList(), problems(answer()))
    }

    @Test
    fun `given a blank answer when validating then should report it`() {
        assertTrue(problems(answer(text = "  ")).any { "empty" in it })
    }

    @Test
    fun `given an unknown intent when validating then should report it`() {
        assertTrue(problems(answer(intent = "gossip")).any { "intent" in it && "gossip" in it })
    }

    @Test
    fun `given a confidence above one when validating then should report it`() {
        assertTrue(problems(answer(confidence = 1.7)).any { "confidence" in it && "1.7" in it })
    }

    @Test
    fun `given an escalation without a ticket when validating then should report it`() {
        assertTrue(problems(answer(ticketId = null)).any { "ticket_id" in it })
    }

    @Test
    fun `given a ticket that is not in the tool results when validating then should report it as invented`() {
        val invented = answer(text = "Ticket TCK-9999 was opened.", ticketId = "TCK-9999")

        assertTrue(problems(invented).any { "TCK-9999" in it && "tool results" in it })
    }

    @Test
    fun `given a ticket missing from the customer text when validating then should report it`() {
        assertTrue(problems(answer(text = "A human will help you.")).any { "TCK-1001" in it && "text" in it })
    }

    @Test
    fun `given low confidence without escalating when validating then should report it`() {
        val unsure = answer(confidence = 0.3, escalated = false, ticketId = null, text = "Maybe.")

        assertTrue(problems(unsure).any { "confidence" in it && "escalat" in it })
    }

    @Test
    fun `given low confidence with an escalation when validating then should accept it`() {
        assertEquals(emptyList(), problems(answer(confidence = 0.3)))
    }

    @Test
    fun `given a source that appears in the tool results ignoring case when validating then should accept it`() {
        val cited = answer(sources = listOf("returns policy"), escalated = false, ticketId = null, text = "Returns take 30 days.")

        assertEquals(emptyList(), problems(cited))
    }

    @Test
    fun `given a source that is not in the tool results when validating then should report it`() {
        val cited = answer(sources = listOf("Warranty"), escalated = false, ticketId = null, text = "Covered.")

        assertTrue(problems(cited).any { "Warranty" in it && "tool results" in it })
    }

    @Test
    fun `given a specialist that answered NO_ANSWER while sources are listed when validating then should report the sources`() {
        val cited = answer(sources = listOf("Returns Policy"), escalated = false, ticketId = null, text = "Sorry.")

        assertTrue(problems(cited, evidence = listOf("NO_ANSWER")).any { "Returns Policy" in it })
    }

    @Test
    fun `given several mistakes at once when validating then should report all of them`() {
        val bad = answer(text = "", intent = "gossip", confidence = 2.0)

        assertTrue(problems(bad).size >= 3)
    }
}
