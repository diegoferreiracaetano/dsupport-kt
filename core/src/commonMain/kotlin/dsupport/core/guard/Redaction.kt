package dsupport.core.guard

import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** National ids and card numbers never reach a ticket, even if the model copies them from the conversation. */
class Redaction : Hook {
    override val covers = setOf("create_ticket")

    override suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision {
        if (tool.name !in covers) return Decision.Allow
        val clean = JsonObject(
            args.mapValues { (key, value) ->
                if (key == "customer_email" || value !is JsonPrimitive || !value.isString) value
                else JsonPrimitive(Patterns.redact(value.content))
            },
        )
        return if (clean == args) Decision.Allow else Decision.Modify(clean)
    }
}
