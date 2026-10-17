package dsupport.testing

import kotlinx.serialization.json.JsonObject

/** What the provider will answer in a contract test. Each LlmClient translates it to its own format. */
sealed interface Scenario {
    data class Text(val text: String) : Scenario
    data class ToolCall(val id: String, val tool: String, val args: JsonObject) : Scenario
    data class Structured(val value: JsonObject) : Scenario
}
