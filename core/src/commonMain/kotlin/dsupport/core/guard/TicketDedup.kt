package dsupport.core.guard

import dsupport.core.port.TicketGateway
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import dsupport.core.tools.stringArg
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * One open ticket per customer. A creation in flight reserves the customer, so simultaneous calls
 * cannot both pass. Keep it last among the guards of `create_ticket`: a later denial would leave the reservation held.
 */
class TicketDedup(private val tickets: TicketGateway) : Hook {
    override val covers = setOf("create_ticket")

    private val lock = Mutex()
    private val inFlight = mutableSetOf<String>()

    override suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision {
        val email = customerOf(tool, args) ?: return Decision.Allow
        return lock.withLock {
            if (email in inFlight) {
                return@withLock Decision.Deny("A ticket for this customer is already being created. Do not open another.")
            }
            val existing = tickets.findOpenByEmail(email)
            if (existing != null) {
                return@withLock Decision.Deny(
                    "Ticket ${existing.ticketId} is already open for this customer. " +
                        "Do not open another; tell the customer this number.",
                )
            }
            inFlight += email
            Decision.Allow
        }
    }

    override suspend fun afterTool(agent: String, tool: ToolSpec, args: JsonObject, result: ToolResult): ToolResult {
        val email = customerOf(tool, args) ?: return result
        lock.withLock { inFlight -= email }
        return result
    }

    private fun customerOf(tool: ToolSpec, args: JsonObject): String? =
        if (tool.name in covers) args.stringArg("customer_email")?.lowercase() else null
}
