package dsupport.adapters.mcp

import dsupport.core.mcp.McpServerConfig
import dsupport.core.mcp.McpServersConfig
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class McpToolProviderTest {

    private val orders = McpServerConfig("orders", listOf("orders-bin"))
    private val tickets = McpServerConfig("tickets", listOf("tickets-bin"))

    private fun spec(name: String) = ToolSpec(name, "does $name", JsonObject(emptyMap()))

    private fun connection(vararg names: String): McpConnection = mockk<McpConnection>(relaxed = true).also {
        coEvery { it.listTools() } returns names.map(::spec)
    }

    private fun connector(vararg servers: Pair<McpServerConfig, McpConnection>): McpConnector =
        mockk<McpConnector>().also { connector ->
            servers.forEach { (config, connection) -> coEvery { connector.connect(config) } returns connection }
        }

    @Test
    fun `given two servers when listing tools then should return the tools of both`() = runTest {
        val provider = McpToolProvider(
            McpServersConfig(listOf(orders, tickets)),
            connector(orders to connection("get_order_status"), tickets to connection("create_ticket")),
        )

        val names = provider.tools().map { it.spec.name }

        assertEquals(listOf("get_order_status", "create_ticket"), names)
    }

    @Test
    fun `given a remote tool when running it then should delegate to the connection with the same name and arguments`() = runTest {
        val connection = connection("get_order_status")
        val args = buildJsonObject { put("order_id", JsonPrimitive("1042")) }
        coEvery { connection.callTool("get_order_status", args) } returns ToolResult("shipped")
        val provider = McpToolProvider(McpServersConfig(listOf(orders)), connector(orders to connection))

        val result = provider.tools().single().run(args)

        assertEquals(ToolResult("shipped"), result)
    }

    @Test
    fun `given a call that exceeds the timeout when running a tool then should return an error result instead of hanging`() = runTest {
        val connection = connection("slow")
        coEvery { connection.callTool("slow", any()) } coAnswers {
            delay(60_000)
            ToolResult("late")
        }
        val provider = McpToolProvider(McpServersConfig(listOf(orders)), connector(orders to connection), callTimeoutMs = 1_000)

        val result = provider.tools().single().run(JsonObject(emptyMap()))

        assertTrue(result.isError)
        assertTrue(result.content.startsWith("error:"))
    }

    @Test
    fun `given a connection that throws when running a tool then should return an error result`() = runTest {
        val connection = connection("broken")
        coEvery { connection.callTool("broken", any()) } throws IllegalStateException("pipe closed")
        val provider = McpToolProvider(McpServersConfig(listOf(orders)), connector(orders to connection))

        val result = provider.tools().single().run(JsonObject(emptyMap()))

        assertEquals(ToolResult("error: pipe closed", isError = true), result)
    }

    @Test
    fun `given a server that fails to connect when listing tools then should throw McpServerException naming that server`() = runTest {
        val failing = mockk<McpConnector>()
        coEvery { failing.connect(orders) } throws McpServerException("orders", "could not start")
        val provider = McpToolProvider(McpServersConfig(listOf(orders)), failing)

        val error = assertFailsWith<McpServerException> { provider.tools() }

        assertEquals("orders", error.server)
    }

    @Test
    fun `given two servers exposing the same tool name when listing tools then should throw McpServerException`() = runTest {
        val provider = McpToolProvider(
            McpServersConfig(listOf(orders, tickets)),
            connector(orders to connection("lookup"), tickets to connection("lookup")),
        )

        val error = assertFailsWith<McpServerException> { provider.tools() }

        assertTrue(error.message!!.contains("lookup"))
    }

    @Test
    fun `given tools listed twice when listing again then should connect only once`() = runTest {
        val connector = connector(orders to connection("get_order_status"))
        val provider = McpToolProvider(McpServersConfig(listOf(orders)), connector)

        provider.tools()
        provider.tools()

        coVerify(exactly = 1) { connector.connect(orders) }
    }

    @Test
    fun `given a provider when closing it then should close every connection`() = runTest {
        val first = connection("a")
        val second = connection("b")
        every { first.close() } returns Unit
        every { second.close() } returns Unit
        val provider = McpToolProvider(McpServersConfig(listOf(orders, tickets)), connector(orders to first, tickets to second))
        provider.tools()

        provider.close()

        verify { first.close() }
        verify { second.close() }
    }
}
