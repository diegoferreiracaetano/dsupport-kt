package dsupport.core.tool

import kotlinx.serialization.json.JsonObject

/** The contract the model reads to decide: name, description and the JSON Schema of the input. */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val readOnly: Boolean = true,
    val destructive: Boolean = false,
)

data class ToolResult(val content: String, val isError: Boolean = false)

/** A capability of the agent, local or remote (MCP). The agent cannot tell the difference. */
interface Tool {
    val spec: ToolSpec
    suspend fun run(args: JsonObject): ToolResult
}

/** Where tools come from: a local list, an MCP server, and so on. */
interface ToolProvider {
    suspend fun tools(): List<Tool>
}
