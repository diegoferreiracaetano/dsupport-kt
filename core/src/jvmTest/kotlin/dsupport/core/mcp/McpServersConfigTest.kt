package dsupport.core.mcp

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class McpServersConfigTest {

    @Test
    fun `given a JSON file with two servers when decoding then should keep names commands and environments`() {
        val text = """
            {"servers": [
              {"name": "orders", "command": ["bin/orders", "--fast"], "env": {"DATA_DIR": "data"}},
              {"name": "tickets", "command": ["bin/tickets"]}
            ]}
        """.trimIndent()

        val config = Json.decodeFromString(McpServersConfig.serializer(), text)

        assertEquals(
            McpServersConfig(
                listOf(
                    McpServerConfig("orders", listOf("bin/orders", "--fast"), mapOf("DATA_DIR" to "data")),
                    McpServerConfig("tickets", listOf("bin/tickets")),
                ),
            ),
            config,
        )
    }
}
