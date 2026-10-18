package dsupport.mcp.orders

import dsupport.adapters.mcp.McpServerException
import dsupport.adapters.mcp.McpToolProvider
import dsupport.adapters.mcp.StdioMcpConnector
import dsupport.core.mcp.McpServerConfig
import dsupport.core.mcp.McpServersConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StdioMcpConnectorTest {

    private val dataDir = File("../data").canonicalPath

    private fun realServer(): McpServerConfig = McpServerConfig(
        name = "orders",
        command = listOf(
            File(System.getProperty("java.home"), "bin/java").path,
            "-cp",
            System.getProperty("java.class.path"),
            "dsupport.mcp.orders.MainKt",
        ),
        env = mapOf("DATA_DIR" to dataDir, "REFERENCE_DATE" to "2026-10-15"),
    )

    @Test
    fun `given the real orders server when listing tools then should find get_order_status and track_shipment`() = runBlocking {
        val connection = StdioMcpConnector().connect(realServer())

        val names = connection.listTools().map { it.name }

        connection.close()
        assertEquals(listOf("get_order_status", "track_shipment"), names.sorted())
    }

    @Test
    fun `given the real orders server when calling get_order_status then should return the order of the data file`() = runBlocking {
        val connection = StdioMcpConnector().connect(realServer())

        val result = connection.callTool(
            "get_order_status",
            buildJsonObject {
                put("order_id", JsonPrimitive("1001"))
                put("email", JsonPrimitive("ana@example.com"))
            },
        )

        connection.close()
        assertTrue(!result.isError, result.content)
        assertTrue(result.content.contains("1001"), result.content)
    }

    @Test
    fun `given a command that does not exist when connecting then should throw McpServerException`() {
        val missing = McpServerConfig("ghost", listOf("/definitely/not/a/binary"))

        assertFailsWith<McpServerException> { runBlocking { StdioMcpConnector().connect(missing) } }
    }

    @Test
    fun `given a server that dies while connected when calling a tool then should return an error result through the provider`() = runBlocking {
        val config = McpServersConfig(listOf(realServer()))
        val provider = McpToolProvider(config, StdioMcpConnector(), callTimeoutMs = 5_000)
        val tool = provider.tools().first { it.spec.name == "get_order_status" }
        ProcessHandle.current().children().filter { it.info().commandLine().orElse("").contains("dsupport.mcp.orders.MainKt") }
            .forEach { it.destroyForcibly() }
        Thread.sleep(500)

        val result = tool.run(buildJsonObject { put("order_id", JsonPrimitive("1001")) })

        provider.close()
        assertTrue(result.isError)
    }
}
