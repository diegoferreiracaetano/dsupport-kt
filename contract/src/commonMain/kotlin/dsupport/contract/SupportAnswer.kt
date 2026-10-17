package dsupport.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val INTENTS = listOf("policy", "order", "return", "complaint", "other")

/**
 * The final answer of the assistant. Every field has a default so a partial model reply still decodes;
 * what is wrong with it is the job of the validator, not of the decoder.
 */
@Serializable
data class SupportAnswer(
    val answer: String = "",
    val intent: String = "other",
    val sources: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val escalated: Boolean = false,
    @SerialName("ticket_id") val ticketId: String? = null,
) {
    companion object {
        private val lenient = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun fromJson(json: JsonObject): SupportAnswer = lenient.decodeFromJsonElement(serializer(), json)
    }
}
