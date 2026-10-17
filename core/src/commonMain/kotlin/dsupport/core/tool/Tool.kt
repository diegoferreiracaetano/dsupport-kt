package dsupport.core.tool

import kotlinx.serialization.json.JsonObject

/** O contrato que o modelo "lê" para decidir: nome, descrição e JSON Schema da entrada. */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val readOnly: Boolean = true,
    val destructive: Boolean = false,
)

data class ToolResult(val content: String, val isError: Boolean = false)

/** Uma capacidade do agente. Local (Kotlin no processo) ou remota (MCP): o agente não percebe a diferença. */
interface Tool {
    val spec: ToolSpec
    suspend fun run(args: JsonObject): ToolResult
}

/** De onde vêm as ferramentas: uma lista local, um servidor MCP, etc. */
interface ToolProvider {
    suspend fun tools(): List<Tool>
}
