package dsupport.core.tools

import dsupport.core.domain.TicketPriority
import dsupport.core.port.TicketGateway
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateTicketToolTest {
    private val tickets = mockk<TicketGateway>()
    private val tool = CreateTicketTool(tickets)

    private fun validArgs() = argsOf(
        "summary" to "Order 1042 is ten days late",
        "priority" to "high",
        "customer_email" to "ana@example.com",
        "reason" to "delay",
    )

    @Test
    fun `given valid arguments when creating a ticket then should create it and return its id`() = runTest {
        coEvery {
            tickets.create("Order 1042 is ten days late", TicketPriority.HIGH, "ana@example.com", "delay")
        } returns "TCK-1001"

        val result = tool.run(validArgs()).json()

        assertTrue(result.getValue("ok").jsonPrimitive.boolean)
        assertEquals("TCK-1001", result.getValue("ticket_id").jsonPrimitive.content)
    }

    @Test
    fun `given an unknown priority when creating a ticket then should answer invalid_priority without creating it`() = runTest {
        val args = argsOf(
            "summary" to "Order 1042 is ten days late",
            "priority" to "urgent",
            "customer_email" to "ana@example.com",
            "reason" to "delay",
        )

        val result = tool.run(args).json()

        assertEquals("invalid_priority", result.getValue("error").jsonPrimitive.content)
        coVerify(exactly = 0) { tickets.create(any(), any(), any(), any()) }
    }

    @Test
    fun `given a summary that is too short when creating a ticket then should answer summary_too_short`() = runTest {
        val args = argsOf(
            "summary" to "late",
            "priority" to "low",
            "customer_email" to "ana@example.com",
            "reason" to "delay",
        )

        val result = tool.run(args).json()

        assertEquals("summary_too_short", result.getValue("error").jsonPrimitive.content)
        coVerify(exactly = 0) { tickets.create(any(), any(), any(), any()) }
    }

    @Test
    fun `given a missing customer email when creating a ticket then should answer missing_argument`() = runTest {
        val args = argsOf("summary" to "Order 1042 is ten days late", "priority" to "high", "reason" to "delay")

        val result = tool.run(args).json()

        assertEquals("missing_argument:customer_email", result.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `given the tool when reading its spec then should be marked destructive and not read only`() {
        assertEquals("create_ticket", tool.spec.name)
        assertTrue(tool.spec.destructive)
        assertFalse(tool.spec.readOnly)
    }
}
