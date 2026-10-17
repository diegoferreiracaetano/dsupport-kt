package dsupport.adapters.persistence

import dsupport.core.domain.TicketPriority
import dsupport.core.domain.TicketStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTicketGatewayTest {
    private val now = "2026-10-15T10:00:00Z"
    private val file: Path = createTempDirectory().resolve("tickets.json")
    private val gateway = JsonTicketGateway(file) { now }

    private suspend fun JsonTicketGateway.open(email: String) =
        create("Order 1042 is ten days late", TicketPriority.HIGH, email, "delay")

    @Test
    fun `given no tickets file when creating the first ticket then should number it TCK-1001 and persist it`() = runTest {
        val id = gateway.open("ana@example.com")

        assertEquals("TCK-1001", id)
        assertTrue("TCK-1001" in file.readText())
    }

    @Test
    fun `given an existing ticket when creating another then should number it TCK-1002`() = runTest {
        gateway.open("ana@example.com")

        val id = gateway.open("bruno@example.com")

        assertEquals("TCK-1002", id)
    }

    @Test
    fun `given several open tickets of one customer when finding the open one then should return the most recent ignoring case`() = runTest {
        gateway.open("ana@example.com")
        gateway.open("ana@example.com")

        val ticket = gateway.findOpenByEmail("ANA@example.com")

        assertNotNull(ticket)
        assertEquals("TCK-1002", ticket.ticketId)
        assertEquals(TicketStatus.OPEN, ticket.status)
        assertEquals(now, ticket.createdAt)
    }

    @Test
    fun `given only a closed ticket when finding the open one then should return null`() = runTest {
        file.writeText(
            """[{"ticket_id":"TCK-1001","created_at":"$now","status":"closed","summary":"Order 1042 is ten days late",
               "priority":"high","customer_email":"ana@example.com","reason":"delay"}]""",
        )

        assertNull(gateway.findOpenByEmail("ana@example.com"))
    }

    @Test
    fun `given many concurrent creations when creating tickets then should give each one a distinct number`() = runTest {
        val ids = coroutineScope {
            (1..20).map { index -> async(Dispatchers.Default) { gateway.open("customer$index@example.com") } }.awaitAll()
        }

        assertEquals(20, ids.toSet().size)
        assertEquals((1001..1020).map { "TCK-$it" }.toSet(), ids.toSet())
    }

    @Test
    fun `given a persisted ticket when another gateway reads the same file then should find it`() = runTest {
        gateway.open("ana@example.com")

        val found = JsonTicketGateway(file) { now }.findOpenByEmail("ana@example.com")

        assertNotNull(found)
        assertEquals("TCK-1001", found.ticketId)
    }
}
