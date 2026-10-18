package dsupport.adapters.mcp

import dsupport.core.mcp.McpServersConfig
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolProvider
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_CALL_TIMEOUT_MS = 15_000L

/** Tools served by MCP servers. Connects once, lazily, and turns every failure of a remote call into an error result. */
class McpToolProvider(
    private val config: McpServersConfig,
    private val connector: McpConnector = StdioMcpConnector(),
    private val callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
) : ToolProvider, AutoCloseable {
    private val lock = Mutex()
    private val connections = mutableListOf<McpConnection>()
    private var cached: List<Tool>? = null

    override suspend fun tools(): List<Tool> = lock.withLock {
        cached ?: load().also { cached = it }
    }

    override fun close() {
        connections.forEach { it.close() }
        connections.clear()
        cached = null
    }

    private suspend fun load(): List<Tool> {
        val owners = mutableMapOf<String, String>()
        val tools = mutableListOf<Tool>()
        for (server in config.servers) {
            val connection = connector.connect(server)
            connections += connection
            for (spec in connection.listTools()) {
                val other = owners.put(spec.name, server.name)
                if (other != null) {
                    throw McpServerException(server.name, "tool '${spec.name}' is exposed by both '$other' and '${server.name}'")
                }
                tools += RemoteTool(spec, connection, callTimeoutMs)
            }
        }
        return tools
    }

    private class RemoteTool(
        override val spec: ToolSpec,
        private val connection: McpConnection,
        private val timeoutMs: Long,
    ) : Tool {
        override suspend fun run(args: JsonObject): ToolResult = try {
            withTimeout(timeoutMs) { connection.callTool(spec.name, args) }
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) ToolResult("error: ${spec.name} timed out after $timeoutMs ms", isError = true) else throw e
        } catch (e: Exception) {
            ToolResult("error: ${e.message ?: e::class.simpleName}", isError = true)
        }
    }
}
