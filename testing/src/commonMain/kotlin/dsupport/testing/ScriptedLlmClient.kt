package dsupport.testing

import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Returns canned replies in order and records every request. For tests only. */
class ScriptedLlmClient(
    private val replies: List<LlmReply>,
    private val structuredReplies: List<String> = emptyList(),
) : LlmClient {
    private var nextReply = 0
    private var nextStructured = 0

    val received = mutableListOf<LlmRequest>()

    override suspend fun next(request: LlmRequest): LlmReply {
        received += request.copy(messages = request.messages.toList())
        return replies.getOrNull(nextReply++)
            ?: error("ScriptedLlmClient: no scripted replies left (request #$nextReply)")
    }

    override suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject {
        received += request.copy(messages = request.messages.toList())
        val raw = structuredReplies.getOrNull(nextStructured++)
            ?: error("ScriptedLlmClient: no structured replies left (request #$nextStructured)")
        return Json.parseToJsonElement(raw).jsonObject
    }

    companion object {
        private val usage = Usage(inputTokens = 1, outputTokens = 1)

        fun from(scenario: Scenario): ScriptedLlmClient = when (scenario) {
            is Scenario.Text -> ScriptedLlmClient(listOf(LlmReply.Text(scenario.text, usage)))
            is Scenario.ToolCall -> ScriptedLlmClient(
                listOf(LlmReply.ToolCalls(listOf(ToolCall(scenario.id, scenario.tool, scenario.args)), usage)),
            )
            is Scenario.Structured -> ScriptedLlmClient(emptyList(), listOf(scenario.value.toString()))
        }
    }
}
