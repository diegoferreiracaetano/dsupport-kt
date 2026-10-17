package dsupport.core.guard

import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject

/** Cleans national ids and card numbers from the result of any tool, including specialist agents. */
class RedactResults : Hook {
    override suspend fun afterTool(agent: String, tool: ToolSpec, args: JsonObject, result: ToolResult): ToolResult =
        result.copy(content = Patterns.redact(result.content))
}
