package dsupport.core.guard

import dsupport.core.tool.ToolSpec
import dsupport.core.tools.stringArg
import kotlinx.serialization.json.JsonObject

/** Order data is only looked up with a numeric order number and a well-formed email. */
class IdentityGate : Hook {
    override val covers = setOf("get_order_status", "track_shipment")

    private val digits = Regex("""\d+""")

    override suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision = when {
        tool.name !in covers -> Decision.Allow
        args.stringArg("order_id")?.let { digits.matches(it) } != true ->
            Decision.Deny("order_id is missing or invalid. Ask the customer for the order number.")
        args.stringArg("email")?.let { Patterns.email.matches(it) } != true ->
            Decision.Deny("email is missing or invalid. Ask the customer for the email used in the purchase.")
        else -> Decision.Allow
    }
}
