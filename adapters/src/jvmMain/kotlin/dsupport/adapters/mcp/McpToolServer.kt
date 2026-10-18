package dsupport.adapters.mcp

import dsupport.core.tool.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.InputStream
import java.io.OutputStream

/** Serves any list of [Tool] as an MCP server over stdio. */
class McpToolServer(private val name: String, private val version: String, private val tools: List<Tool>) {

    suspend fun serve(input: InputStream = System.`in`, output: OutputStream = System.out) {
        System.setOut(System.err)
        val server = Server(
            Implementation(name, version),
            ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        )
        tools.forEach { register(server, it) }
        val closed = CompletableDeferred<Unit>()
        val session = server.createSession(StdioServerTransport(input.asSource().buffered(), output.asSink().buffered()))
        session.onClose { closed.complete(Unit) }
        closed.await()
    }

    private fun register(server: Server, tool: Tool) {
        val spec = tool.spec
        server.addTool(
            name = spec.name,
            description = spec.description,
            inputSchema = ToolSchema(
                properties = spec.inputSchema["properties"] as? JsonObject,
                required = (spec.inputSchema["required"] as? JsonArray)?.map { (it as JsonPrimitive).content },
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = spec.readOnly, destructiveHint = spec.destructive),
        ) { request ->
            val result = tool.run(request.arguments ?: JsonObject(emptyMap()))
            CallToolResult(content = listOf(TextContent(result.content)), isError = result.isError)
        }
    }
}
