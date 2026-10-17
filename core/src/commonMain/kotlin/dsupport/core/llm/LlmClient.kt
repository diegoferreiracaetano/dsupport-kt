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

/** A structured answer plus what it cost, so the cost of structured calls is as visible as any other call. */
data class StructuredReply(val value: JsonObject, val usage: Usage)

/** A mandatory output format: name, description and the JSON Schema the answer must follow. */
data class OutputSchema(val name: String, val description: String, val schema: JsonObject)

/** The only thing the core knows about a language model. Each provider is an adapter. */
interface LlmClient {
    suspend fun next(request: LlmRequest): LlmReply
    suspend fun structured(request: LlmRequest, schema: OutputSchema): StructuredReply
}
