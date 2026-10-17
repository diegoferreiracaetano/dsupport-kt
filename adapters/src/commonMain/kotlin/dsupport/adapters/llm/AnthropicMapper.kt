package dsupport.adapters.llm

import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.StructuredReply
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Translates between the provider-neutral LLM vocabulary and the Anthropic Messages API JSON. */
class AnthropicMapper {

    fun buildRequest(
        modelId: String,
        maxTokens: Int,
        request: LlmRequest,
        schema: OutputSchema? = null,
    ): JsonObject = buildJsonObject {
        put("model", modelId)
        put("max_tokens", maxTokens)
        put("system", request.system)
        putJsonObject("thinking") { put("type", "disabled") }
        if (request.tools.isNotEmpty()) put("tools", JsonArray(request.tools.map { toolToApi(it) }))
        put("messages", JsonArray(toTurns(request.messages).map { turnToApi(it) }))
        if (schema != null) {
            putJsonObject("output_config") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", schema.schema)
                }
            }
        }
    }

    fun parseReply(body: JsonObject): LlmReply {
        val blocks = contentBlocks(body)
        val usage = usageOf(body)
        val toolUses = blocks.filter { it.type() == "tool_use" }
        if (toolUses.isNotEmpty()) {
            return LlmReply.ToolCalls(
                toolUses.map {
                    ToolCall(
                        id = it.getValue("id").jsonPrimitive.content,
                        tool = it.getValue("name").jsonPrimitive.content,
                        args = it.getValue("input").jsonObject,
                    )
                },
                usage,
            )
        }
        return LlmReply.Text(joinedText(blocks), usage)
    }

    fun parseStructured(body: JsonObject): StructuredReply {
        val blocks = contentBlocks(body)
        if (stopReason(body) == "max_tokens") throw LlmApiException(null, "structured output was cut off by max_tokens")
        val text = joinedText(blocks)
        return try {
            StructuredReply(Json.parseToJsonElement(text).jsonObject, usageOf(body))
        } catch (e: SerializationException) {
            throw LlmApiException(null, "structured output is not valid JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw LlmApiException(null, "structured output is not a JSON object: ${e.message}")
        }
    }

    private class Turn(val role: String, val blocks: List<JsonObject>)

    private fun toolToApi(spec: ToolSpec) = buildJsonObject {
        put("name", spec.name)
        put("description", spec.description)
        put("input_schema", spec.inputSchema)
    }

    private fun toTurns(messages: List<Message>): List<Turn> {
        val turns = mutableListOf<Turn>()
        for (message in messages) {
            val (role, blocks) = blocksOf(message)
            val last = turns.lastOrNull()
            if (last != null && last.role == role) {
                turns[turns.lastIndex] = Turn(role, last.blocks + blocks)
            } else {
                turns += Turn(role, blocks)
            }
        }
        return turns
    }

    private fun turnToApi(turn: Turn): JsonObject {
        val (results, others) = turn.blocks.partition { it.type() == "tool_result" }
        return buildJsonObject {
            put("role", turn.role)
            put("content", JsonArray(results + others))
        }
    }

    private fun blocksOf(message: Message): Pair<String, List<JsonObject>> = when (message) {
        is Message.User -> "user" to listOf(textBlock(message.text))
        is Message.AssistantText -> "assistant" to listOf(textBlock(message.text))
        is Message.AssistantToolCalls -> "assistant" to message.calls.map {
            buildJsonObject {
                put("type", "tool_use")
                put("id", it.id)
                put("name", it.tool)
                put("input", it.args)
            }
        }
        is Message.ToolResult -> "user" to listOf(
            buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", message.callId)
                put("content", message.content)
                if (message.isError) put("is_error", true)
            },
        )
    }

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun contentBlocks(body: JsonObject): List<JsonObject> {
        if (stopReason(body) == "refusal") throw LlmApiException(null, "the model refused the request")
        val content = body["content"] as? JsonArray ?: throw LlmApiException(null, "response has no content")
        if (content.isEmpty()) throw LlmApiException(null, "response content is empty")
        val blocks = content.map { it.jsonObject }
        if (blocks.any { it.type() == "thinking" || it.type() == "redacted_thinking" }) {
            throw LlmApiException(null, "thinking blocks are not supported, keep thinking disabled")
        }
        return blocks
    }

    private fun joinedText(blocks: List<JsonObject>): String =
        blocks.filter { it.type() == "text" }.joinToString("\n") { it.getValue("text").jsonPrimitive.content }

    private fun usageOf(body: JsonObject): Usage {
        val usage = body["usage"] as? JsonObject
        return Usage(
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0,
        )
    }

    private fun stopReason(body: JsonObject): String? = body["stop_reason"]?.jsonPrimitive?.content

    private fun JsonObject.type(): String = getValue("type").jsonPrimitive.content
}
