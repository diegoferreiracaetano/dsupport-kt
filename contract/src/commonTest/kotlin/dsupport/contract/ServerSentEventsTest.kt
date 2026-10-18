package dsupport.contract

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerSentEventsTest {

    private val started = AgentEvent.AgentStarted("orchestrator", 0, "hi", "r1")

    @Test
    fun `given an event when framing then should write one data line and a blank line`() {
        val frame = ServerSentEvents.frame(started)

        assertEquals("data: ${ServerSentEvents.json.encodeToString(AgentEvent.serializer(), started)}\n\n", frame)
    }

    @Test
    fun `given an error message when framing then should write an error event without line breaks inside`() {
        val frame = ServerSentEvents.errorFrame("boom\nsecond line")

        assertEquals("event: error\ndata: boom second line\n\n", frame)
    }

    @Test
    fun `given the frames of two events when parsing line by line then should deliver both events in order`() {
        val completed = AgentEvent.Escalated("TCK-1001", "needs a human")
        val parser = SseParser()

        val items = (ServerSentEvents.frame(started) + ServerSentEvents.frame(completed)).lines().mapNotNull(parser::accept)

        assertEquals(listOf(StreamItem.Event(started), StreamItem.Event(completed)), items)
    }

    @Test
    fun `given an error frame when parsing then should deliver the message as a failure`() {
        val parser = SseParser()

        val items = ServerSentEvents.errorFrame("boom").lines().mapNotNull(parser::accept)

        assertEquals(listOf(StreamItem.Failure("boom")), items)
    }

    @Test
    fun `given lines that are not data when parsing then should ignore them`() {
        val parser = SseParser()

        val items = listOf(": keep-alive", "", "id: 7").mapNotNull(parser::accept)

        assertEquals(emptyList(), items)
    }

    @Test
    fun `given a request when encoding and decoding then should keep the question`() {
        val text = ServerSentEvents.json.encodeToString(ChatRequest.serializer(), ChatRequest("where is my order?"))

        assertEquals(ChatRequest("where is my order?"), ServerSentEvents.json.decodeFromString(ChatRequest.serializer(), text))
    }
}
