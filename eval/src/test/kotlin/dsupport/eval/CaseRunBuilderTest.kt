package dsupport.eval

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaseRunBuilderTest {
    private val builder = CaseRunBuilder()
    private val answer = SupportAnswer(answer = "Done, ticket TCK-1001.", ticketId = "TCK-1001", escalated = true)

    private val events = listOf(
        AgentEvent.AgentStarted("orchestrator", 0, "question"),
        AgentEvent.ToolFinished("orders", "get_order_status", "specialist internal result", false, 10),
        AgentEvent.ToolFinished("orchestrator", "orders", "late by 10 days", false, 2000),
        AgentEvent.ToolFinished("orchestrator", "escalation", "TCK-1001", false, 2000),
        AgentEvent.Completed(answer),
    )

    @Test
    fun `given the events of a run when building then should take the answer from the completed event`() {
        assertEquals(answer, builder.build(events, "[]", 1500).answer)
    }

    @Test
    fun `given the events of a run when building then should use only what the orchestrator received as evidence`() {
        val evidence = builder.build(events, "[]", 1500).evidence

        assertTrue("late by 10 days" in evidence && "TCK-1001" in evidence)
        assertFalse("specialist internal result" in evidence)
    }

    @Test
    fun `given the tickets file of the case when building then should count it as evidence for tickets opened by code`() {
        val evidence = builder.build(events, """[{"ticket_id":"TCK-1002"}]""", 1500).evidence

        assertTrue(evidence.any { "TCK-1002" in it })
    }

    @Test
    fun `given the elapsed time when building then should keep it and keep the events`() {
        val run = builder.build(events, "[]", 1500)

        assertEquals(1500, run.elapsedMs)
        assertEquals(events, run.events)
    }

    @Test
    fun `given events without a completed answer when building then should fail`() {
        assertFailsWith<IllegalStateException> { builder.build(events.dropLast(1), "[]", 1500) }
    }
}
