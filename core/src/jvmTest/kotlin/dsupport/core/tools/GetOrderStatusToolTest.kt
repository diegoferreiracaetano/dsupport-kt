package dsupport.core.tools

import dsupport.core.domain.Order
import dsupport.core.domain.OrderItem
import dsupport.core.domain.OrderStatus
import dsupport.core.port.OrderGateway
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetOrderStatusToolTest {
    private val orders = mockk<OrderGateway>()
    private val today = LocalDate(2026, 10, 15)
    private val tool = GetOrderStatusTool(orders) { today }

    private fun order(status: OrderStatus, eta: LocalDate) = Order(
        orderId = "1042",
        email = "ana@example.com",
        status = status,
        eta = eta,
        carrier = "FastShip",
        tracking = "FS100100",
        total = 89.9,
        items = listOf(OrderItem("MOU-1", "Wireless Mouse", 2)),
    )

    private fun lookup() = argsOf("order_id" to "1042", "email" to "ana@example.com")

    @Test
    fun `given an in transit order past its estimate when getting its status then should report it late with the days of delay`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns order(OrderStatus.IN_TRANSIT, LocalDate(2026, 10, 5))

        val result = tool.run(lookup()).json()

        assertTrue(result.getValue("ok").jsonPrimitive.boolean)
        assertEquals("in_transit", result.getValue("status").jsonPrimitive.content)
        assertTrue(result.getValue("late").jsonPrimitive.boolean)
        assertEquals(10, result.getValue("days_late").jsonPrimitive.int)
        assertEquals("2x Wireless Mouse", result.getValue("items").jsonPrimitive.content)
    }

    @Test
    fun `given a delivered order past its estimate when getting its status then should not report it late`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns order(OrderStatus.DELIVERED, LocalDate(2026, 10, 5))

        val result = tool.run(lookup()).json()

        assertFalse(result.getValue("late").jsonPrimitive.boolean)
        assertEquals(0, result.getValue("days_late").jsonPrimitive.int)
    }

    @Test
    fun `given an order due today when getting its status then should not report it late`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns order(OrderStatus.IN_TRANSIT, today)

        val result = tool.run(lookup()).json()

        assertFalse(result.getValue("late").jsonPrimitive.boolean)
    }

    @Test
    fun `given an unknown order or a wrong email when getting its status then should answer with a business failure and no execution error`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns null

        val result = tool.run(lookup())

        assertFalse(result.isError)
        assertFalse(result.json().getValue("ok").jsonPrimitive.boolean)
        assertEquals("order_not_found_or_email_mismatch", result.json().getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `given a missing email when getting the status then should answer missing_argument without querying orders`() = runTest {
        val result = tool.run(argsOf("order_id" to "1042"))

        assertEquals("missing_argument:email", result.json().getValue("error").jsonPrimitive.content)
        coVerify(exactly = 0) { orders.findOwned(any(), any()) }
    }

    @Test
    fun `given the tool when reading its spec then should require order_id and email and stay read only`() {
        val spec = tool.spec

        assertEquals("get_order_status", spec.name)
        assertEquals(
            listOf("order_id", "email"),
            spec.inputSchema.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(spec.readOnly)
        assertFalse(spec.destructive)
    }
}
