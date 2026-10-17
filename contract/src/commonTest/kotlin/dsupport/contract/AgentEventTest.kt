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
    fun `given a completed event when serializing then should carry the answer`() {
        val event: AgentEvent = AgentEvent.Completed(SupportAnswer(answer = "Your order arrives tomorrow."))
        val text = json.encodeToString(AgentEvent.serializer(), event)
        assertEquals(event, json.decodeFromString(AgentEvent.serializer(), text))
    }
}
