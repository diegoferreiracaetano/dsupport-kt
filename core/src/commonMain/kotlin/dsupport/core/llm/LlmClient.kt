package dsupport.core.llm

import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject

data class Usage(val inputTokens: Int, val outputTokens: Int)

data class LlmRequest(val system: String, val messages: List<Message>, val tools: List<ToolSpec>)

sealed interface LlmReply {
    val usage: Usage

    data class Text(val text: String, override val usage: Usage) : LlmReply
    data class ToolCalls(val calls: List<ToolCall>, override val usage: Usage) : LlmReply
}

/** Um formato de saída obrigatório: nome, descrição e o JSON Schema que a resposta tem que seguir. */
data class OutputSchema(val name: String, val description: String, val schema: JsonObject)

/** O "cérebro". O core só conhece esta porta; cada provedor é um adaptador. */
interface LlmClient {
    suspend fun next(request: LlmRequest): LlmReply
    suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject
}
