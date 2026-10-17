package dsupport.core.tools

import dsupport.core.domain.TicketPriority
import dsupport.core.port.TicketGateway
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MIN_SUMMARY_LENGTH = 10

class CreateTicketTool(private val tickets: TicketGateway) : Tool {

    override val spec = ToolSpec(
        name = "create_ticket",
        description = "Opens a ticket for a human agent. Use it when an order is late, the customer is unhappy or " +
            "the case cannot be solved automatically. Priority is low, medium or high.",
        inputSchema = stringSchema("summary", "priority", "customer_email", "reason"),
        readOnly = false,
        destructive = true,
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val summary = args.stringArg("summary") ?: return fail("missing_argument:summary")
        val priorityText = args.stringArg("priority") ?: return fail("missing_argument:priority")
        val email = args.stringArg("customer_email") ?: return fail("missing_argument:customer_email")
        val reason = args.stringArg("reason") ?: return fail("missing_argument:reason")
        val priority = TicketPriority.fromWire(priorityText) ?: return fail("invalid_priority")
        if (summary.length < MIN_SUMMARY_LENGTH) return fail("summary_too_short")
        return ok("ticket_id" to JsonPrimitive(tickets.create(summary, priority, email, reason)))
    }
}
