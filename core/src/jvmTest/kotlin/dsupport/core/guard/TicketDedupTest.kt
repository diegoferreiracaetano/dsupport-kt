package dsupport.core.guard

import dsupport.core.domain.Ticket
import dsupport.core.domain.TicketPriority
import dsupport.core.domain.TicketStatus
import dsupport.core.port.TicketGateway
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import dsupport.core.tools.argsOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TicketDedupTest {
    private val tickets = mockk<TicketGateway>()
    private val dedup = TicketDedup(tickets)
    private val createTicket = ToolSpec("create_ticket", "desc", buildJsonObject { put("type", "object") })
    private val args = argsOf("summary" to "Order 1042 is ten days late", "customer_email" to "ana@example.com")

    private fun openTicket() = Ticket(
        "TCK-1001", "2026-10-15T10:00:00Z", TicketStatus.OPEN, "Order 1042 is ten days late",
        TicketPriority.HIGH, "ana@example.com", "delay",
    )

    @Test
    fun `given an open ticket for the customer when creating another then should deny and give the existing number`() = runTest {
        coEvery { tickets.findOpenByEmail("ana@example.com") } returns openTicket()

        val decision = dedup.beforeTool("escalation", createTicket, args)

        assertTrue("TCK-1001" in assertIs<Decision.Deny>(decision).reason)
    }

    @Test
    fun `given no open ticket when creating one then should allow the call`() = runTest {
        coEvery { tickets.findOpenByEmail(any()) } returns null

        assertEquals(Decision.Allow, dedup.beforeTool("escalation", createTicket, args))
    }

    @Test
    fun `given a creation already in flight for the same customer when another starts then should deny it`() = runTest {
        coEvery { tickets.findOpenByEmail(any()) } returns null
        dedup.beforeTool("escalation", createTicket, args)

        val second = dedup.beforeTool("escalation", createTicket, args)

        assertIs<Decision.Deny>(second)
    }

    @Test
    fun `given many simultaneous creations for the same customer when checking then should allow exactly one`() = runTest {
        coEvery { tickets.findOpenByEmail(any()) } returns null

        val decisions = coroutineScope {
            (1..20).map { async(Dispatchers.Default) { dedup.beforeTool("escalation", createTicket, args) } }.awaitAll()
        }

        assertEquals(1, decisions.count { it == Decision.Allow })
    }

    @Test
    fun `given a failed creation when the tool finishes then should release the claim so a retry is allowed`() = runTest {
        coEvery { tickets.findOpenByEmail(any()) } returns null
        dedup.beforeTool("escalation", createTicket, args)

        dedup.afterTool("escalation", createTicket, args, ToolResult("""{"ok":false,"error":"summary_too_short"}"""))

        assertEquals(Decision.Allow, dedup.beforeTool("escalation", createTicket, args))
    }

    @Test
    fun `given a different customer when creating a ticket then should allow it even with another claim in flight`() = runTest {
        coEvery { tickets.findOpenByEmail(any()) } returns null
        dedup.beforeTool("escalation", createTicket, args)

        val other = argsOf("summary" to "Order 1050 is late", "customer_email" to "bruno@example.com")

        assertEquals(Decision.Allow, dedup.beforeTool("escalation", createTicket, other))
    }

    @Test
    fun `given a tool it does not cover when checking then should allow the call without querying tickets`() = runTest {
        val search = ToolSpec("search_kb", "desc", buildJsonObject { put("type", "object") })

        assertEquals(Decision.Allow, dedup.beforeTool("knowledge", search, argsOf("query" to "refund")))
    }
}
