package dsupport.adapters.persistence

import dsupport.core.domain.OrderStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonOrderGatewayTest {
    private val sample = """
        [
          {"order_id":"1042","email":"ana@example.com","status":"in_transit","eta":"2026-10-05",
           "carrier":"FastShip","tracking":"FS100100","total":89.9,
           "items":[{"sku":"MOU-1","name":"Wireless Mouse","qty":2}]}
        ]
    """.trimIndent()

    private fun gatewayWith(json: String): JsonOrderGateway {
        val file = createTempFile(suffix = ".json")
        file.writeText(json)
        return JsonOrderGateway(file)
    }

    @Test
    fun `given the matching email in another case when finding an owned order then should return the order`() = runTest {
        val order = gatewayWith(sample).findOwned("1042", "ANA@example.com")

        assertNotNull(order)
        assertEquals(OrderStatus.IN_TRANSIT, order.status)
        assertEquals(LocalDate(2026, 10, 5), order.eta)
        assertEquals("Wireless Mouse", order.items.single().name)
    }

    @Test
    fun `given a wrong email when finding an owned order then should return null like an unknown order`() = runTest {
        val gateway = gatewayWith(sample)

        assertNull(gateway.findOwned("1042", "bruno@example.com"))
        assertNull(gateway.findOwned("9999", "ana@example.com"))
    }

    @Test
    fun `given an unknown status in the file when loading then should fail loudly`() = runTest {
        val gateway = gatewayWith(sample.replace("in_transit", "teleported"))

        assertFailsWith<IllegalArgumentException> { gateway.findOwned("1042", "ana@example.com") }
    }

    @Test
    fun `given the project orders file when loading every order then should have unique ids and cover every status`() = runTest {
        val orders = JsonOrderGateway(Path.of("..", "data", "orders.json")).all()

        assertEquals(orders.size, orders.map { it.orderId }.toSet().size)
        assertEquals(OrderStatus.entries.toSet(), orders.map { it.status }.toSet())
        assertTrue(orders.all { order -> order.items.isNotEmpty() && order.items.all { it.qty > 0 } })
        assertTrue(orders.all { it.email.endsWith("@example.com") })
    }
}
