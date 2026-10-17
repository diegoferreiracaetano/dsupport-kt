package dsupport.core.tools

import dsupport.core.domain.OrderStatus
import dsupport.core.port.OrderGateway
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

class GetOrderStatusTool(
    private val orders: OrderGateway,
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
) : Tool {

    override val spec = ToolSpec(
        name = "get_order_status",
        description = "Looks up the status, delivery estimate and delay of an order. Use it when the customer asks " +
            "whether an order arrived or is late. Requires order_id and the email used in the purchase.",
        inputSchema = stringSchema("order_id", "email"),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val orderId = args.stringArg("order_id") ?: return fail("missing_argument:order_id")
        val email = args.stringArg("email") ?: return fail("missing_argument:email")
        val order = orders.findOwned(orderId, email) ?: return fail("order_not_found_or_email_mismatch")
        val now = today()
        val late = order.status == OrderStatus.IN_TRANSIT && order.eta < now
        return ok(
            "order_id" to JsonPrimitive(order.orderId),
            "status" to JsonPrimitive(order.status.wire),
            "eta" to JsonPrimitive(order.eta.toString()),
            "late" to JsonPrimitive(late),
            "days_late" to JsonPrimitive(if (late) order.eta.daysUntil(now) else 0),
            "items" to JsonPrimitive(order.items.joinToString { "${it.qty}x ${it.name}" }),
        )
    }
}
