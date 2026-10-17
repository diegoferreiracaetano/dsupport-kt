package dsupport.core.guard

import dsupport.core.tool.ToolSpec
import dsupport.core.tools.argsOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RedactionTest {
    private val redaction = Redaction()
    private val schema = buildJsonObject { put("type", "object") }
    private val createTicket = ToolSpec("create_ticket", "desc", schema)

    private suspend fun modified(args: JsonObject): JsonObject =
        assertIs<Decision.Modify>(redaction.beforeTool("escalation", createTicket, args)).args

    @Test
    fun `given a national id and a card number in the summary when creating a ticket then should replace them with placeholders`() = runTest {
        val args = argsOf(
            "summary" to "Order 1042 late, customer id 123.456.789-00, card 4111 1111 1111 1111",
            "priority" to "high",
            "customer_email" to "ana@example.com",
            "reason" to "delay",
        )

        val summary = modified(args).getValue("summary").jsonPrimitive.content

        assertFalse("123.456" in summary)
        assertFalse("4111" in summary)
        assertEquals("Order 1042 late, customer id [ID REMOVED], card [CARD REMOVED]", summary)
    }

    @Test
    fun `given a US social security number when creating a ticket then should replace it as well`() = runTest {
        val args = argsOf("summary" to "SSN 123-45-6789 given by mistake", "customer_email" to "ana@example.com")

        assertEquals("SSN [ID REMOVED] given by mistake", modified(args).getValue("summary").jsonPrimitive.content)
    }

    @Test
    fun `given sensitive text when creating a ticket then should never touch the customer email`() = runTest {
        val args = argsOf("summary" to "card 4111 1111 1111 1111 declined", "customer_email" to "ana@example.com")

        assertEquals("ana@example.com", modified(args).getValue("customer_email").jsonPrimitive.content)
    }

    @Test
    fun `given clean arguments when creating a ticket then should allow the call unchanged`() = runTest {
        val args = argsOf("summary" to "Order 1042 is ten days late", "customer_email" to "ana@example.com")

        assertEquals(Decision.Allow, redaction.beforeTool("escalation", createTicket, args))
    }

    @Test
    fun `given sensitive text sent to a tool it does not cover when checking then should allow the call`() = runTest {
        val search = ToolSpec("search_kb", "desc", schema)

        val decision = redaction.beforeTool("knowledge", search, argsOf("query" to "123.456.789-00"))

        assertEquals(Decision.Allow, decision)
    }
}
