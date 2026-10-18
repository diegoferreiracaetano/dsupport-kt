package dsupport.core.trace

import dsupport.contract.AgentEvent
import dsupport.contract.GuardAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceReportTest {
    private val report = TraceReport()

    private val nested = listOf(
        AgentEvent.AgentStarted("orchestrator", 0, "question", runId = "r1"),
        AgentEvent.ModelCalled("orchestrator", 100, 10, 50, runId = "r1"),
        AgentEvent.ToolStarted("orchestrator", "orders", "{}", runId = "r1"),
        AgentEvent.AgentStarted("orders", 1, "check", runId = "r2", parentRunId = "r1"),
        AgentEvent.ModelCalled("orders", 30, 5, 20, runId = "r2"),
        AgentEvent.ToolFinished("orchestrator", "orders", "late", false, 80, runId = "r1"),
        AgentEvent.ModelCalled("reviewer", 40, 4, 30, runId = "reviewer"),
    )

    @Test
    fun `given an orchestrator that calls one specialist when building the tree then should nest the specialist under the tool call that started it`() {
        val expected = """
            [orchestrator] r1
              model call: 100 in / 10 out tokens, 50 ms
              tool orders: 80 ms
                [orders] r2
                  model call: 30 in / 5 out tokens, 20 ms
            [reviewer]
              model call: 40 in / 4 out tokens, 30 ms
        """.trimIndent()

        assertEquals(expected, report.tree(nested))
    }

    @Test
    fun `given two specialists with the same name running in parallel when building the tree then should keep their events apart`() {
        val events = listOf(
            AgentEvent.AgentStarted("orchestrator", 0, "q", runId = "r1"),
            AgentEvent.ToolStarted("orchestrator", "orders", "{a}", runId = "r1"),
            AgentEvent.ToolStarted("orchestrator", "orders", "{b}", runId = "r1"),
            AgentEvent.AgentStarted("orders", 1, "a", runId = "r2", parentRunId = "r1"),
            AgentEvent.AgentStarted("orders", 1, "b", runId = "r3", parentRunId = "r1"),
            AgentEvent.ModelCalled("orders", 11, 1, 5, runId = "r3"),
            AgentEvent.ModelCalled("orders", 22, 2, 6, runId = "r2"),
            AgentEvent.ToolFinished("orchestrator", "orders", "ra", false, 70, runId = "r1"),
            AgentEvent.ToolFinished("orchestrator", "orders", "rb", false, 90, runId = "r1"),
        )

        val lines = report.tree(events).lines()

        val firstTool = lines.indexOfFirst { it.trim().startsWith("tool orders") }
        val secondTool = lines.indexOfLast { it.trim().startsWith("tool orders") }
        val r2 = lines.indexOfFirst { "22 in" in it }
        val r3 = lines.indexOfFirst { "11 in" in it }
        assertTrue(firstTool < r2 && r2 < secondTool && secondTool < r3, lines.joinToString("\n"))
    }

    @Test
    fun `given a tool that did not finish when building the tree then should mark it as running`() {
        val events = listOf(
            AgentEvent.AgentStarted("orchestrator", 0, "q", runId = "r1"),
            AgentEvent.ToolStarted("orchestrator", "orders", "{}", runId = "r1"),
        )

        assertTrue("tool orders: running" in report.tree(events))
    }

    @Test
    fun `given a failed tool when building the tree then should flag the error`() {
        val events = listOf(
            AgentEvent.AgentStarted("orders", 1, "q", runId = "r1"),
            AgentEvent.ToolStarted("orders", "get_order_status", "{}", runId = "r1"),
            AgentEvent.ToolFinished("orders", "get_order_status", "boom", true, 12, runId = "r1"),
        )

        assertTrue("tool get_order_status: ERROR 12 ms" in report.tree(events))
    }

    @Test
    fun `given a guard action when building the tree then should show it under the run it affected`() {
        val events = listOf(
            AgentEvent.AgentStarted("orders", 1, "q", runId = "r1"),
            AgentEvent.GuardActed("orders", "IdentityGate", "get_order_status", GuardAction.DENY, "email missing", runId = "r1"),
        )

        assertTrue("  guard IdentityGate DENY on get_order_status: email missing" in report.tree(events))
    }

    @Test
    fun `given events from an old trace without run ids when building the tree then should still show them`() {
        val events = listOf(AgentEvent.ModelCalled("orchestrator", 5, 1, 9))

        assertEquals("[orchestrator]\n  model call: 5 in / 1 out tokens, 9 ms", report.tree(events))
    }

    @Test
    fun `given 2 orchestrator turns of 1000 input tokens and 1 specialist turn of 300 when summing by agent then should return 2000 and 300`() {
        val events = listOf(
            AgentEvent.ModelCalled("orchestrator", 1000, 100, 400, runId = "r1"),
            AgentEvent.ModelCalled("orders", 300, 20, 150, runId = "r2"),
            AgentEvent.ModelCalled("orchestrator", 1000, 50, 300, runId = "r1"),
        )

        val costs = report.byAgent(events)

        assertEquals(listOf("orchestrator", "orders"), costs.map { it.agent })
        assertEquals(AgentCost("orchestrator", 2, 2000, 150, 700), costs[0])
        assertEquals(AgentCost("orders", 1, 300, 20, 150), costs[1])
    }

    @Test
    fun `given a reviewer call when summing by agent then should include the reviewer`() {
        assertEquals(listOf("reviewer"), report.byAgent(nested.filter { it.runIdOrNull() == "reviewer" }).map { it.agent })
    }

    @Test
    fun `given events with durations when asking for the slowest then should return the top ones among model and tool events`() {
        val events = listOf(
            AgentEvent.ModelCalled("a", 1, 1, 50, runId = "r1"),
            AgentEvent.ToolFinished("a", "t", "x", false, 900, runId = "r1"),
            AgentEvent.ModelCalled("a", 1, 1, 400, runId = "r1"),
            AgentEvent.AgentStarted("a", 0, "q", runId = "r1"),
            AgentEvent.ToolFinished("a", "u", "x", false, 100, runId = "r1"),
        )

        val slowest = report.slowest(events, 2)

        assertEquals(2, slowest.size)
        assertEquals(900L, (slowest[0] as AgentEvent.ToolFinished).durationMs)
        assertEquals(400L, (slowest[1] as AgentEvent.ModelCalled).durationMs)
    }

    @Test
    fun `given no events when reporting then should return empty results`() {
        assertEquals("", report.tree(emptyList()))
        assertEquals(emptyList(), report.byAgent(emptyList()))
        assertEquals(emptyList(), report.slowest(emptyList()))
    }

    private fun AgentEvent.runIdOrNull(): String? = (this as? AgentEvent.ModelCalled)?.runId
}
