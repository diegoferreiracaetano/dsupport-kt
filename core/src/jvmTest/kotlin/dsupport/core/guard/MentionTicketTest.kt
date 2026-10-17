package dsupport.core.guard

import dsupport.core.llm.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MentionTicketTest {
    private val guard = MentionTicket()

    private fun historyWith(vararg results: String): List<Message> = listOf(Message.User("question")) +
        results.mapIndexed { index, content -> Message.ToolResult("t$index", "create_ticket", content, isError = false) }

    @Test
    fun `given a created ticket missing from the answer when finishing then should append the ticket number`() = runTest {
        val history = historyWith("""{"ok":true,"ticket_id":"TCK-1001"}""")

        val text = guard.beforeFinal("orchestrator", history, "A human agent will take care of it.")

        assertEquals("A human agent will take care of it.\n\nYour ticket number: TCK-1001.", text)
    }

    @Test
    fun `given a ticket already mentioned in the answer when finishing then should leave the answer unchanged`() = runTest {
        val history = historyWith("""{"ok":true,"ticket_id":"TCK-1001"}""")

        val text = guard.beforeFinal("orchestrator", history, "We opened ticket TCK-1001 for you.")

        assertEquals("We opened ticket TCK-1001 for you.", text)
    }

    @Test
    fun `given no ticket in the tool results when finishing then should leave the answer unchanged`() = runTest {
        val text = guard.beforeFinal("orchestrator", historyWith("""{"ok":true}"""), "Your order arrives tomorrow.")

        assertEquals("Your order arrives tomorrow.", text)
    }

    @Test
    fun `given two tickets in the results and one mentioned when finishing then should append only the missing one`() = runTest {
        val history = historyWith("""{"ticket_id":"TCK-1001"}""", """{"ticket_id":"TCK-1002"}""")

        val text = guard.beforeFinal("orchestrator", history, "See TCK-1001.")

        assertEquals("See TCK-1001.\n\nYour ticket number: TCK-1002.", text)
    }
}
