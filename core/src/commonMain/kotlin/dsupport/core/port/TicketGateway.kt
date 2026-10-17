package dsupport.core.port

import dsupport.core.domain.Ticket
import dsupport.core.domain.TicketPriority

interface TicketGateway {
    /** Creates an open ticket and returns its id. */
    suspend fun create(summary: String, priority: TicketPriority, customerEmail: String, reason: String): String

    suspend fun findOpenByEmail(email: String): Ticket?
}
