package dsupport.adapters.llm

import dsupport.core.llm.LlmClient
import dsupport.testing.LlmClientContract
import dsupport.testing.Scenario
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AnthropicLlmClientContractTest : LlmClientContract() {
    override fun clientFor(scenario: Scenario): LlmClient {
        val body = jsonFor(scenario).toString()
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return AnthropicLlmClient(apiKey = "test-key", http = HttpClient(engine))
    }

    private fun jsonFor(scenario: Scenario): JsonObject {
        val block = when (scenario) {
            is Scenario.Text -> textBlock(scenario.text)
            is Scenario.ToolCall -> buildJsonObject {
                put("type", "tool_use")
                put("id", scenario.id)
                put("name", scenario.tool)
                put("input", scenario.args)
            }
            is Scenario.Structured -> textBlock(scenario.value.toString())
        }
        return buildJsonObject {
            put("content", buildJsonArray { add(block) })
            put("stop_reason", "end_turn")
            put("usage", buildJsonObject {
                put("input_tokens", 1)
                put("output_tokens", 1)
            })
        }
    }

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
    }
}
