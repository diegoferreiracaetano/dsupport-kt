package dsupport.core.tools

import dsupport.core.port.OrderGateway
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TrackShipmentTool(private val orders: OrderGateway) : Tool {

    override val spec = ToolSpec(
        name = "track_shipment",
        description = "Returns the carrier and the tracking code of an order. Use it when the customer asks for " +
            "tracking or wants to follow the delivery. Requires order_id and the email used in the purchase.",
        inputSchema = stringSchema("order_id", "email"),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val orderId = args.stringArg("order_id") ?: return fail("missing_argument:order_id")
        val email = args.stringArg("email") ?: return fail("missing_argument:email")
        val order = orders.findOwned(orderId, email) ?: return fail("order_not_found_or_email_mismatch")
        if (order.tracking.isBlank()) return fail("shipment_not_dispatched")
        return ok(
            "carrier" to JsonPrimitive(order.carrier),
            "tracking" to JsonPrimitive(order.tracking),
        )
    }
}
