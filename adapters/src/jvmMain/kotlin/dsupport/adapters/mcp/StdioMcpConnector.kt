package dsupport.adapters.mcp

import dsupport.core.mcp.McpServerConfig
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private const val DEFAULT_START_TIMEOUT_MS = 10_000L

/** Starts an MCP server as a child process and talks to it over stdio, through the official SDK. */
class StdioMcpConnector(private val startTimeoutMs: Long = DEFAULT_START_TIMEOUT_MS) : McpConnector {

    override suspend fun connect(server: McpServerConfig): McpConnection {
        val process = try {
            ProcessBuilder(server.command).apply {
                environment().putAll(server.env)
                redirectError(ProcessBuilder.Redirect.INHERIT)
            }.start()
        } catch (e: Exception) {
            throw McpServerException(server.name, "could not start '${server.command.joinToString(" ")}': ${e.message}", e)
        }
        val client = Client(Implementation("dsupport", "1"))
        try {
            withTimeout(startTimeoutMs) {
                client.connect(StdioClientTransport(process.inputStream.asSource().buffered(), process.outputStream.asSink().buffered()))
            }
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw if (e is kotlinx.coroutines.TimeoutCancellationException) McpServerException(server.name, "did not answer within $startTimeoutMs ms", e) else e
        } catch (e: Exception) {
            process.destroyForcibly()
            throw McpServerException(server.name, "could not connect: ${e.message}", e)
        }
        return SdkConnection(server.name, client, process)
    }

    private class SdkConnection(private val serverName: String, private val client: Client, private val process: Process) : McpConnection {

        override suspend fun listTools(): List<ToolSpec> = client.listTools().tools.map { tool ->
            ToolSpec(
                name = tool.name,
                description = tool.description.orEmpty(),
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    tool.inputSchema.properties?.let { put("properties", it) }
                    tool.inputSchema.required?.let { required -> put("required", kotlinx.serialization.json.JsonArray(required.map(::JsonPrimitive))) }
                },
                readOnly = tool.annotations?.readOnlyHint ?: true,
                destructive = tool.annotations?.destructiveHint ?: false,
            )
        }

        override suspend fun callTool(name: String, args: JsonObject): ToolResult {
            val result = client.callTool(name, args.mapValues { plain(it.value) })
            val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            return ToolResult(text, result.isError ?: false)
        }

        override fun close() {
            runCatching { runBlocking { client.close() } }
            process.destroyForcibly()
        }

        private fun plain(element: JsonElement): Any? = when (element) {
            is JsonNull -> null
            is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
            is JsonObject -> element.mapValues { plain(it.value) }
            else -> element.toString()
        }
    }
}
