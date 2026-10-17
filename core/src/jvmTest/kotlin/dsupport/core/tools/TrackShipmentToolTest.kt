package dsupport.core.tools

import dsupport.core.domain.Order
import dsupport.core.domain.OrderItem
import dsupport.core.domain.OrderStatus
import dsupport.core.port.OrderGateway
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackShipmentToolTest {
    private val orders = mockk<OrderGateway>()
    private val tool = TrackShipmentTool(orders)

    private fun order(carrier: String, tracking: String) = Order(
        "1042", "ana@example.com", OrderStatus.IN_TRANSIT, LocalDate(2026, 10, 5), carrier, tracking, 89.9,
        listOf(OrderItem("MOU-1", "Wireless Mouse", 1)),
    )

    private fun lookup() = argsOf("order_id" to "1042", "email" to "ana@example.com")

    @Test
    fun `given a dispatched order when tracking it then should return the carrier and the tracking code`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns order("FastShip", "FS100100")

        val result = tool.run(lookup()).json()

        assertTrue(result.getValue("ok").jsonPrimitive.boolean)
        assertEquals("FastShip", result.getValue("carrier").jsonPrimitive.content)
        assertEquals("FS100100", result.getValue("tracking").jsonPrimitive.content)
    }

    @Test
    fun `given an order that was not dispatched yet when tracking it then should answer shipment_not_dispatched`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns order("", "")

        val result = tool.run(lookup()).json()

        assertFalse(result.getValue("ok").jsonPrimitive.boolean)
        assertEquals("shipment_not_dispatched", result.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `given an unknown order or a wrong email when tracking it then should answer order_not_found_or_email_mismatch`() = runTest {
        coEvery { orders.findOwned("1042", "ana@example.com") } returns null

        val result = tool.run(lookup()).json()

        assertEquals("order_not_found_or_email_mismatch", result.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `given a missing order id when tracking then should answer missing_argument`() = runTest {
        val result = tool.run(argsOf("email" to "ana@example.com")).json()

        assertEquals("missing_argument:order_id", result.getValue("error").jsonPrimitive.content)
    }
}
