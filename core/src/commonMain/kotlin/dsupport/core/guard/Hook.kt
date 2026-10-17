package dsupport.core.guard

import dsupport.core.llm.Message
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * Deterministic code that runs around the model at fixed points of the agent loop.
 * The model cannot see it or skip it. A guard only acts on the tools it lists in [covers].
 */
interface Hook {
    val name: String get() = this::class.simpleName.orEmpty()

    val covers: Set<String> get() = emptySet()

    suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision = Decision.Allow

    suspend fun afterTool(agent: String, tool: ToolSpec, args: JsonObject, result: ToolResult): ToolResult = result

    suspend fun beforeFinal(agent: String, history: List<Message>, text: String): String = text
}
