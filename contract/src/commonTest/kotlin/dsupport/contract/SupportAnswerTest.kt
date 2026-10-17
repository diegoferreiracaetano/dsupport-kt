package dsupport.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupportAnswerTest {
    private fun parse(raw: String) = SupportAnswer.fromJson(Json.parseToJsonElement(raw) as JsonObject)

    @Test
    fun `given a JSON with only the answer when decoding then should use the defaults for the rest`() {
        val answer = parse("""{"answer":"Hi"}""")

        assertEquals(SupportAnswer(answer = "Hi"), answer)
        assertEquals("other", answer.intent)
        assertEquals(emptyList(), answer.sources)
        assertEquals(0.0, answer.confidence)
        assertNull(answer.ticketId)
    }

    @Test
    fun `given the snake case ticket_id when decoding then should fill ticketId`() {
        val answer = parse("""{"answer":"Done","escalated":true,"ticket_id":"TCK-1001","confidence":0.9,"intent":"order"}""")

        assertEquals("TCK-1001", answer.ticketId)
        assertEquals(true, answer.escalated)
        assertEquals("order", answer.intent)
    }

    @Test
    fun `given extra fields when decoding then should ignore them`() {
        val answer = parse("""{"answer":"Hi","mood":"happy"}""")

        assertEquals("Hi", answer.answer)
    }

    @Test
    fun `given a null ticket_id when decoding then should keep it null`() {
        assertNull(parse("""{"answer":"Hi","ticket_id":null}""").ticketId)
    }

    @Test
    fun `given a null in a field that cannot be null when decoding then should fall back to the default`() {
        assertEquals("other", parse("""{"answer":"Hi","intent":null}""").intent)
    }

    @Test
    fun `given an answer when encoding then should write ticket_id in snake case`() {
        val json = Json.encodeToString(SupportAnswer.serializer(), SupportAnswer(answer = "Hi", ticketId = "TCK-1001"))

        assertEquals(true, "\"ticket_id\":\"TCK-1001\"" in json)
    }

    @Test
    fun `given the list of intents when reading it then should contain the five supported intents`() {
        assertEquals(listOf("policy", "order", "return", "complaint", "other"), INTENTS)
    }
}
