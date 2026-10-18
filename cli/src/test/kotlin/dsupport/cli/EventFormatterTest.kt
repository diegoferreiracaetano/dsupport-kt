package dsupport.cli

import dsupport.contract.AgentEvent
import dsupport.contract.GuardAction
import dsupport.contract.SupportAnswer
import dsupport.core.trace.AgentCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventFormatterTest {
    private val formatter = EventFormatter()

    @Test
    fun `given a model call when formatting then should show the agent the tokens and the time`() {
        val line = formatter.format(AgentEvent.ModelCalled("orchestrator", 900, 100, 400))

        assertEquals("[orchestrator] model call: 900 in / 100 out tokens, 400 ms", line)
    }

    @Test
    fun `given the start of an agent when formatting then should show the agent and the task`() {
        val line = formatter.format(AgentEvent.AgentStarted("orders", 1, "check order 1042"))

        assertEquals("[orders] started: check order 1042", line)
    }

    @Test
    fun `given a very long task when formatting then should cut it`() {
        val line = formatter.format(AgentEvent.AgentStarted("orders", 1, "x".repeat(500)))

        assertTrue(line.length < 160 && line.endsWith("..."))
    }

    @Test
    fun `given a tool call when formatting then should show the agent the tool and the arguments`() {
        val line = formatter.format(AgentEvent.ToolStarted("orchestrator", "orders", """{"task":"check"}"""))

        assertEquals("""[orchestrator] calls orders {"task":"check"}""", line)
    }

    @Test
    fun `given a finished tool when formatting then should show the duration and the result`() {
        val line = formatter.format(AgentEvent.ToolFinished("orders", "get_order_status", "late by 10 days", false, 120))

        assertEquals("[orders] get_order_status finished in 120 ms: late by 10 days", line)
    }

    @Test
    fun `given a failed tool when formatting then should flag the error`() {
        val line = formatter.format(AgentEvent.ToolFinished("orders", "get_order_status", "boom", true, 5))

        assertTrue("ERROR" in line && "boom" in line)
    }

    @Test
    fun `given a guard action on a tool when formatting then should show the guard the action and the reason`() {
        val line = formatter.format(AgentEvent.GuardActed("orders", "IdentityGate", "get_order_status", GuardAction.DENY, "email missing"))

        assertEquals("[orders] guard IdentityGate DENY on get_order_status: email missing", line)
    }

    @Test
    fun `given a guard action on the final answer when formatting then should say it was the answer`() {
        val line = formatter.format(AgentEvent.GuardActed("orchestrator", "MentionTicket", null, GuardAction.REWRITE, "final answer rewritten"))

        assertTrue("on the answer" in line)
    }

    @Test
    fun `given review rounds when formatting then should show the verdict and the problems`() {
        assertEquals("review round 1: approved", formatter.format(AgentEvent.ReviewRound(1, true, emptyList())))
        assertEquals(
            "review round 2: rejected (too long; no delay)",
            formatter.format(AgentEvent.ReviewRound(2, false, listOf("too long", "no delay"))),
        )
    }

    @Test
    fun `given an escalation when formatting then should show the ticket and the reason`() {
        assertEquals(
            "escalated to ticket TCK-1002: review limit reached",
            formatter.format(AgentEvent.Escalated("TCK-1002", "review limit reached")),
        )
    }

    @Test
    fun `given the completed event when formatting then should say the answer is ready`() {
        assertEquals("completed", formatter.format(AgentEvent.Completed(SupportAnswer(answer = "Hi"))))
    }

    @Test
    fun `given costs of two agents when formatting then should print a table with a total`() {
        val costs = listOf(AgentCost("orchestrator", 2, 100, 20, 300), AgentCost("orders", 1, 50, 5, 120))

        val table = EventFormatter().formatCost(costs)

        assertEquals(
            "agent         calls  in tokens  out tokens  model ms\n" +
                "orchestrator      2        100          20       300\n" +
                "orders            1         50           5       120\n" +
                "total             3        150          25       420",
            table,
        )
    }
}
