package dsupport.core.answer

import dsupport.contract.INTENTS
import dsupport.core.llm.OutputSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The JSON Schema the model must follow for its final answer. Ranges are checked in code, the API rejects them. */
object SupportAnswerSchema {

    val output = OutputSchema(
        name = "answer_customer",
        description = "Final answer to the customer in the DSupport structured format.",
        schema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put("answer", property("string", "Text for the customer: short, friendly, in English."))
                    put(
                        "intent",
                        property("string", "Main intent of the customer message.").withEnum(INTENTS),
                    )
                    put(
                        "sources",
                        buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                            put("description", "Titles of the help articles used, exactly as the specialists returned them. Empty if none.")
                        },
                    )
                    put("confidence", property("number", "From 0 to 1: how well the answer is supported by the specialist results."))
                    put("escalated", property("boolean", "True if a ticket for a human agent was opened or already existed."))
                    put(
                        "ticket_id",
                        buildJsonObject {
                            put(
                                "anyOf",
                                JsonArray(
                                    listOf(
                                        buildJsonObject { put("type", "string") },
                                        buildJsonObject { put("type", "null") },
                                    ),
                                ),
                            )
                            put("description", "Ticket number (TCK-...) exactly as it appeared in the results, or null.")
                        },
                    )
                },
            )
            put("required", JsonArray(listOf("answer", "intent", "sources", "confidence", "escalated", "ticket_id").map { JsonPrimitive(it) }))
            put("additionalProperties", false)
        },
    )

    private fun property(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun JsonObject.withEnum(values: List<String>): JsonObject =
        JsonObject(this + ("enum" to JsonArray(values.map { JsonPrimitive(it) })))
}
