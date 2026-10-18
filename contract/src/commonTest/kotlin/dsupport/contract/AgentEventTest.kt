package dsupport.contract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentEventTest {
    private val json = Json

    @Test
    fun `given a model call event when serializing then should round trip through JSON`() {
        val event: AgentEvent = AgentEvent.ModelCalled("orchestrator", 900, 100, 1200)
        val text = json.encodeToString(AgentEvent.serializer(), event)
        assertEquals(event, json.decodeFromString(AgentEvent.serializer(), text))
    }

    @Test
    fun `given an event without run ids in the JSON when decoding then should default them so old traces still load`() {
        val json = """{"type":"model_called","agent":"a","inputTokens":1,"outputTokens":2,"durationMs":3}"""

        val event = Json.decodeFromString(AgentEvent.serializer(), json) as AgentEvent.ModelCalled

        assertEquals(ROOT_RUN, event.runId)
    }

    @Test
    fun `given an agent start with a parent when serializing then should round trip the run ids`() {
        val event: AgentEvent = AgentEvent.AgentStarted("orders", 1, "task", runId = "r2", parentRunId = "r1")

        val text = json.encodeToString(AgentEvent.serializer(), event)

        assertEquals(event, json.decodeFromString(AgentEvent.serializer(), text))
    }

    @Test
    fun `given a completed event when serializing then should carry the answer`() {
        val event: AgentEvent = AgentEvent.Completed(SupportAnswer(answer = "Your order arrives tomorrow."))
        val text = json.encodeToString(AgentEvent.serializer(), event)
        assertEquals(event, json.decodeFromString(AgentEvent.serializer(), text))
    }
}
