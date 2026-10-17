package dsupport.core.review

import dsupport.core.llm.OutputSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The verdict of the reviewer. A missing field means rejection, never approval. */
@Serializable
data class Review(
    val approved: Boolean = false,
    val problems: List<String> = emptyList(),
) {
    companion object {
        private val lenient = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun fromJson(json: JsonObject): Review = lenient.decodeFromJsonElement(serializer(), json)
    }
}

object ReviewSchema {
    val output = OutputSchema(
        name = "review_verdict",
        description = "Verdict of the reviewer on the proposed answer.",
        schema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "approved",
                        buildJsonObject {
                            put("type", "boolean")
                            put("description", "True if the answer can go to the customer.")
                        },
                    )
                    put(
                        "problems",
                        buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                            put("description", "What must change, one actionable item per problem. Empty if approved.")
                        },
                    )
                },
            )
            put("required", JsonArray(listOf(JsonPrimitive("approved"), JsonPrimitive("problems"))))
            put("additionalProperties", false)
        },
    )
}
