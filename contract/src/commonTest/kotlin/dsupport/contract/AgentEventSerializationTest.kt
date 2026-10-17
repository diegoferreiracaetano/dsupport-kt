package dsupport.contract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentEventSerializationTest {
    private val json = Json

    @Test
    fun `evento de modelo vai e volta pelo JSON`() {
        val event: AgentEvent = AgentEvent.ModelCalled("orquestrador", 900, 100, 1200)
        val text = json.encodeToString(AgentEvent.serializer(), event)
        assertEquals(event, json.decodeFromString(AgentEvent.serializer(), text))
    }

    @Test
    fun `evento final carrega a resposta`() {
        val event: AgentEvent = AgentEvent.Completed(SupportAnswer(answer = "Seu pedido chega amanhã."))
        val text = json.encodeToString(AgentEvent.serializer(), event)
        assertEquals(event, json.decodeFromString(AgentEvent.serializer(), text))
    }
}
