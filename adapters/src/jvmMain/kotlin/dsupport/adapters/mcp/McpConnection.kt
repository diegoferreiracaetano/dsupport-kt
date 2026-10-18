package dsupport.adapters.mcp

import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject

/** A live conversation with one MCP server. Hides the SDK and the child process. */
interface McpConnection : AutoCloseable {
    suspend fun listTools(): List<ToolSpec>
    suspend fun callTool(name: String, args: JsonObject): ToolResult
}
