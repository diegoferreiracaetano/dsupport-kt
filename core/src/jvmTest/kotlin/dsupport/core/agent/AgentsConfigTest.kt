package dsupport.core.agent

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentsConfigTest {
    private val projectConfig = Path.of("..", "config", "agents.json")

    @Test
    fun `given the project config when decoding then should list the three specialists with their tools`() {
        val config = Json.decodeFromString(AgentsConfig.serializer(), Files.readString(projectConfig))

        assertEquals(setOf("knowledge", "orders", "escalation"), config.agents.keys)
        assertEquals(listOf("search_kb"), config.agents.getValue("knowledge").tools)
        assertEquals(listOf("get_order_status", "track_shipment"), config.agents.getValue("orders").tools)
        assertEquals(listOf("create_ticket"), config.agents.getValue("escalation").tools)
    }

    @Test
    fun `given an agent without max turns when decoding then should default to four turns`() {
        val config = Json.decodeFromString(AgentsConfig.serializer(), """{"agents":{"x":{"tools":[]}}}""")

        assertEquals(4, config.agents.getValue("x").maxTurns)
    }
}
