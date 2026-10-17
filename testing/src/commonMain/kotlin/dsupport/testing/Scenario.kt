package dsupport.testing

import kotlinx.serialization.json.JsonObject

/** O que o "provedor" vai responder num teste de contrato. Cada LlmClient o traduz para o seu formato. */
sealed interface Scenario {
    data class Text(val text: String) : Scenario
    data class ToolCall(val id: String, val tool: String, val args: JsonObject) : Scenario
    data class Structured(val value: JsonObject) : Scenario
}
