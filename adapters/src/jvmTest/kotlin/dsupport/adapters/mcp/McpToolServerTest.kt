package dsupport.adapters.mcp

import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpToolServerTest {

    private val schema = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject { put("order_id", buildJsonObject { put("type", JsonPrimitive("string")) }) })
        put("required", JsonArray(listOf(JsonPrimitive("order_id"))))
    }

    private fun tool(name: String, result: ToolResult): Tool = mockk<Tool>().also {
        every { it.spec } returns ToolSpec(name, "describes $name", schema)
        coEvery { it.run(any()) } returns result
    }

    private fun withClient(tools: List<Tool>, block: suspend (Client) -> Unit) = runBlocking {
        val clientToServer = PipedOutputStream()
        val serverIn = PipedInputStream(clientToServer)
        val serverToClient = PipedOutputStream()
        val clientIn = PipedInputStream(serverToClient)
        val server = launch(Dispatchers.IO) { McpToolServer("test", "1", tools).serve(serverIn, serverToClient) }
        val client = Client(Implementation("test-client", "1"))
        withTimeout(10_000) {
            client.connect(StdioClientTransport(clientIn.asSource().buffered(), clientToServer.asSink().buffered()))
            block(client)
        }
        client.close()
        server.cancel()
    }

    @Test
    fun `given a tool when a client lists tools then should expose its name description and input schema`() {
        withClient(listOf(tool("get_order_status", ToolResult("ok")))) { client ->
            val listed = client.listTools().tools.single()

            assertEquals("get_order_status", listed.name)
            assertEquals("describes get_order_status", listed.description)
            assertEquals(listOf("order_id"), listed.inputSchema.required)
        }
    }

    @Test
    fun `given a tool when a client calls it then should pass the arguments and return its text`() {
        val tool = tool("get_order_status", ToolResult("shipped"))
        withClient(listOf(tool)) { client ->
            val result = client.callTool("get_order_status", mapOf("order_id" to "1042"))

            assertEquals("shipped", (result.content.single() as TextContent).text)
            coVerify { tool.run(buildJsonObject { put("order_id", JsonPrimitive("1042")) }) }
        }
    }

    @Test
    fun `given a tool that returns an error result when a client calls it then should mark the MCP result as an error`() {
        withClient(listOf(tool("get_order_status", ToolResult("order_not_found", isError = true)))) { client ->
            val result = client.callTool("get_order_status", mapOf("order_id" to "x"))

            assertEquals(true, result.isError)
            assertEquals("order_not_found", (result.content.single() as TextContent).text)
        }
    }

    @Test
    fun `given a call without arguments when a client calls the tool then should run it with an empty object`() {
        val tool = tool("get_order_status", ToolResult("ok"))
        withClient(listOf(tool)) { client ->
            client.callTool("get_order_status", emptyMap())

            coVerify { tool.run(JsonObject(emptyMap())) }
            assertTrue(true)
        }
    }
}
