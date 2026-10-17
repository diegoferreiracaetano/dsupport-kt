package dsupport.cli

import dsupport.adapters.persistence.JsonTicketGateway
import dsupport.core.agent.Agent
import dsupport.core.guard.Redaction
import dsupport.core.guard.TicketDedup
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import dsupport.core.tools.CreateTicketTool
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscalationAgentTest {
    private val usage = Usage(10, 5)
    private val file = createTempDirectory().resolve("tickets.json")
    private val gateway = JsonTicketGateway(file) { "2026-10-15T10:00:00Z" }
    private val llm = mockk<LlmClient>()
    private val requests = mutableListOf<LlmRequest>()

    private fun agent() = Agent(
        name = "escalation",
        llm = llm,
        system = "You open tickets.",
        tools = listOf(CreateTicketTool(gateway)),
        hooks = listOf(Redaction(), TicketDedup(gateway)),
    )

    private fun ticketCall(id: String, summary: String = "Order 1042 is ten days late") = ToolCall(
        id,
        "create_ticket",
        buildJsonObject {
            put("summary", summary)
            put("priority", "high")
            put("customer_email", "ana@example.com")
            put("reason", "delay")
        },
    )

    private fun repliesAre(vararg replies: LlmReply) {
        coEvery { llm.next(capture(requests)) } returnsMany replies.toList()
    }

    private fun storedTickets() = Regex("\"ticket_id\"").findAll(file.readText()).count()

    private fun results() = requests[1].messages.filterIsInstance<Message.ToolResult>()

    @Test
    fun `given two create_ticket calls for the same customer in one turn when the agent runs then should create exactly one ticket and block the other`() = runTest {
        repliesAre(LlmReply.ToolCalls(listOf(ticketCall("t1"), ticketCall("t2")), usage), LlmReply.Text("done", usage))

        agent().run("open a ticket")

        assertEquals(1, storedTickets())
        assertEquals(1, results().count { "TCK-1001" in it.content })
        assertEquals(1, results().count { "blocked by TicketDedup" in it.content })
    }

    @Test
    fun `given many truly parallel create_ticket calls for the same customer when the agent runs then should still create exactly one ticket`() = runTest {
        val calls = (1..10).map { ticketCall("t$it") }
        repliesAre(LlmReply.ToolCalls(calls, usage), LlmReply.Text("done", usage))

        withContext(Dispatchers.Default) { agent().run("open a ticket") }

        assertEquals(1, storedTickets())
        assertEquals(9, results().count { it.isError })
    }

    @Test
    fun `given a summary with a national id when the agent runs then should store the ticket redacted`() = runTest {
        repliesAre(
            LlmReply.ToolCalls(listOf(ticketCall("t1", "Order 1042 late, customer id 123.456.789-00")), usage),
            LlmReply.Text("done", usage),
        )

        agent().run("open a ticket")

        val stored = file.readText()
        assertFalse("123.456" in stored)
        assertTrue("[ID REMOVED]" in stored)
    }
}
