package dsupport.core.guard

import dsupport.core.tool.ToolSpec
import dsupport.core.tools.argsOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentityGateTest {
    private val gate = IdentityGate()
    private val schema = buildJsonObject { put("type", "object") }
    private fun spec(name: String) = ToolSpec(name, "desc", schema)

    @Test
    fun `given a valid order number and email when checking an order tool then should allow the call`() = runTest {
        val decision = gate.beforeTool("orders", spec("get_order_status"), argsOf("order_id" to "1042", "email" to "ana@example.com"))

        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `given a missing email when checking track_shipment then should deny and tell the model to ask for the email`() = runTest {
        val decision = gate.beforeTool("orders", spec("track_shipment"), argsOf("order_id" to "1042"))

        val denied = assertIs<Decision.Deny>(decision)
        assertTrue("email" in denied.reason.lowercase())
    }

    @Test
    fun `given a malformed email when checking an order tool then should deny`() = runTest {
        val decision = gate.beforeTool("orders", spec("get_order_status"), argsOf("order_id" to "1042", "email" to "not-an-email"))

        assertIs<Decision.Deny>(decision)
    }

    @Test
    fun `given a non numeric order number when checking an order tool then should deny and tell the model to ask for the order number`() = runTest {
        val decision = gate.beforeTool("orders", spec("get_order_status"), argsOf("order_id" to "abc", "email" to "ana@example.com"))

        val denied = assertIs<Decision.Deny>(decision)
        assertTrue("order" in denied.reason.lowercase())
    }

    @Test
    fun `given a tool it does not cover when checking then should allow the call`() = runTest {
        val decision = gate.beforeTool("knowledge", spec("search_kb"), argsOf("query" to "refund"))

        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `given the gate when reading what it covers then should list the order tools`() {
        assertEquals(setOf("get_order_status", "track_shipment"), gate.covers)
    }
}
