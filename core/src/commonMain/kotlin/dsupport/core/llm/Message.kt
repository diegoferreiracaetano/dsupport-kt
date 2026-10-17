package dsupport.core.llm

import kotlinx.serialization.json.JsonObject

data class ToolCall(val id: String, val tool: String, val args: JsonObject)

/** O histórico da conversa, em vocabulário neutro: cada adaptador traduz para o formato do seu provedor. */
sealed interface Message {
    data class User(val text: String) : Message
    data class AssistantToolCalls(val calls: List<ToolCall>) : Message
    data class ToolResult(val callId: String, val tool: String, val content: String, val isError: Boolean) : Message
    data class AssistantText(val text: String) : Message
}
