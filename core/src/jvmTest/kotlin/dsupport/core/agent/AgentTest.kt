package dsupport.core.agent

import dsupport.contract.AgentEvent
import dsupport.contract.GuardAction
import dsupport.core.guard.Decision
import dsupport.core.guard.Hook
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.StructuredReply
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentTest {
    private val usage = Usage(inputTokens = 10, outputTokens = 5)
    private val emptySchema = buildJsonObject { put("type", "object") }
    private val llm = mockk<LlmClient>()
    private val requests = mutableListOf<LlmRequest>()
    private val events = mutableListOf<AgentEvent>()
    private val sink = EventSink { events += it }

    private val echo = mockk<Tool>().also { tool ->
        every { tool.spec } returns ToolSpec("echo", "Returns the received text.", emptySchema)
        coEvery { tool.run(any()) } returns ToolResult("echo: hi")
    }

    private fun repliesAre(vararg replies: LlmReply) {
        coEvery { llm.next(capture(requests)) } returnsMany replies.toList()
    }

    private fun agent(tools: List<Tool> = listOf(echo), hooks: List<Hook> = emptyList(), maxTurns: Int = 5) =
        Agent("support", llm, "Be friendly.", tools, hooks, sink, maxTurns)

    private fun hook(name: String = "TestHook") = mockk<Hook>().also {
        every { it.name } returns name
        coEvery { it.beforeTool(any(), any(), any()) } returns Decision.Allow
        coEvery { it.afterTool(any(), any(), any(), any()) } answers { arg<ToolResult>(3) }
        coEvery { it.beforeFinal(any(), any(), any()) } answers { arg<String>(2) }
    }

    private fun call(id: String, tool: String) = ToolCall(id, tool, buildJsonObject { put("text", "hi") })

    private fun toolCalls(vararg calls: ToolCall) = LlmReply.ToolCalls(calls.toList(), usage)

    @Test
    fun `given a model that replies with text when running then should return that text`() = runTest {
        repliesAre(LlmReply.Text("Hello!", usage))

        val answer = agent().run("Hi")

        assertEquals("Hello!", answer)
    }

    @Test
    fun `given a model that asks for a tool when running then should run it and send the result back`() = runTest {
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("done", usage))
        val agent = agent()

        val answer = agent.run("echo this")

        assertEquals("done", answer)
        coVerify(exactly = 1) { echo.run(any()) }
        val result = requests[1].messages.filterIsInstance<Message.ToolResult>().single()
        assertEquals("t1", result.callId)
        assertEquals("echo", result.tool)
        assertEquals("echo: hi", result.content)
        assertEquals(listOf("echo: hi"), agent.evidence)
    }

    @Test
    fun `given a model that asks for an unknown tool when running then should send an error result and go on`() = runTest {
        repliesAre(toolCalls(call("t1", "cancel_order")), LlmReply.Text("could not do it", usage))

        val answer = agent().run("cancel it")

        assertEquals("could not do it", answer)
        val result = requests[1].messages.filterIsInstance<Message.ToolResult>().single()
        assertTrue(result.isError)
        assertTrue("cancel_order" in result.content)
    }

    @Test
    fun `given a tool that throws when running then should report the failure as an error result`() = runTest {
        coEvery { echo.run(any()) } throws IllegalStateException("boom")
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("sorry", usage))

        val answer = agent().run("echo this")

        assertEquals("sorry", answer)
        val result = requests[1].messages.filterIsInstance<Message.ToolResult>().single()
        assertTrue(result.isError)
        assertTrue("boom" in result.content)
    }

    @Test
    fun `given a model that never stops asking for tools when running then should stop at the turn limit`() = runTest {
        repliesAre(*Array(5) { toolCalls(call("t$it", "echo")) })

        val answer = agent(maxTurns = 5).run("loop forever")

        assertEquals("Turn limit of 5 reached. Escalating to a human agent.", answer)
        coVerify(exactly = 5) { llm.next(any()) }
    }

    @Test
    fun `given a tool call when running then should emit the events in order`() = runTest {
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("done", usage))

        agent().run("echo this")

        assertEquals(
            listOf("AgentStarted", "ModelCalled", "ToolStarted", "ToolFinished", "ModelCalled"),
            events.map { it::class.simpleName },
        )
        assertIs<AgentEvent.AgentStarted>(events.first())
        val model = events.filterIsInstance<AgentEvent.ModelCalled>().first()
        assertEquals(10, model.inputTokens)
        assertEquals(5, model.outputTokens)
    }

    @Test
    fun `given a finished run when following up then should continue the same conversation`() = runTest {
        repliesAre(LlmReply.Text("first", usage), LlmReply.Text("second", usage))
        val agent = agent()
        agent.run("question")

        val answer = agent.followUp("fix it")

        assertEquals("second", answer)
        assertEquals(
            listOf(Message.User("question"), Message.AssistantText("first"), Message.User("fix it")),
            requests[1].messages,
        )
    }

    @Test
    fun `given a finished run when asking for the final answer then should send the history and the closing request`() = runTest {
        val schema = OutputSchema("answer_format", "The final answer.", emptySchema)
        val structured = mutableListOf<LlmRequest>()
        val expected = buildJsonObject { put("answer", "ok") }
        repliesAre(LlmReply.Text("done", usage))
        coEvery { llm.structured(capture(structured), schema) } returns StructuredReply(expected, Usage(40, 8))
        val agent = agent()
        agent.run("question")

        val result = agent.finish(schema)

        assertEquals(expected, result)
        val finishCall = events.filterIsInstance<AgentEvent.ModelCalled>().last()
        assertEquals(40, finishCall.inputTokens)
        assertEquals(8, finishCall.outputTokens)
        val messages = structured.single().messages
        assertEquals(Message.User("question"), messages.first())
        assertTrue("answer_format" in (messages.last() as Message.User).text)
    }

    @Test
    fun `given a hook that denies the call when the model asks for a tool then should not run it and tell the model it was blocked`() = runTest {
        val blocker = hook("Blocker").also {
            coEvery { it.beforeTool("support", any(), any()) } returns Decision.Deny("not allowed")
        }
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("understood", usage))

        agent(hooks = listOf(blocker)).run("echo this")

        coVerify(exactly = 0) { echo.run(any()) }
        val result = requests[1].messages.filterIsInstance<Message.ToolResult>().single()
        assertTrue(result.isError)
        assertTrue("Blocker" in result.content && "not allowed" in result.content)
        assertTrue(events.contains(AgentEvent.GuardActed("support", "Blocker", "echo", GuardAction.DENY, "not allowed")))
    }

    @Test
    fun `given a hook that modifies the arguments when the model asks for a tool then should run the tool with the new arguments`() = runTest {
        val changed = buildJsonObject { put("text", "clean") }
        val modifier = hook("Modifier").also {
            coEvery { it.beforeTool("support", any(), any()) } returns Decision.Modify(changed)
        }
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("done", usage))

        agent(hooks = listOf(modifier)).run("echo this")

        coVerify { echo.run(changed) }
        assertTrue(events.any { it is AgentEvent.GuardActed && it.action == GuardAction.MODIFY })
    }

    @Test
    fun `given a hook that rewrites results when a tool finishes then should send the rewritten result to the model`() = runTest {
        val rewriter = hook("Rewriter").also {
            coEvery { it.beforeTool(any(), any(), any()) } returns Decision.Allow
            coEvery { it.afterTool("support", any(), any(), any()) } returns ToolResult("rewritten")
        }
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("done", usage))

        agent(hooks = listOf(rewriter)).run("echo this")

        assertEquals("rewritten", requests[1].messages.filterIsInstance<Message.ToolResult>().single().content)
        assertTrue(events.any { it is AgentEvent.GuardActed && it.action == GuardAction.REWRITE && it.tool == "echo" })
    }

    @Test
    fun `given a hook that rewrites the final answer when the model replies then should return the rewritten answer`() = runTest {
        val closer = hook("Closer").also {
            coEvery { it.beforeFinal("support", any(), "done") } returns "done, ticket TCK-1001"
        }
        repliesAre(LlmReply.Text("done", usage))

        val answer = agent(hooks = listOf(closer)).run("hello")

        assertEquals("done, ticket TCK-1001", answer)
        assertTrue(events.contains(AgentEvent.GuardActed("support", "Closer", null, GuardAction.REWRITE, "final answer rewritten")))
    }

    @Test
    fun `given two hooks when the model asks for a tool then should consult them in order and stop at the first denial`() = runTest {
        val first = hook("First").also {
            coEvery { it.beforeTool(any(), any(), any()) } returns Decision.Deny("first says no")
        }
        val second = hook("Second")
        repliesAre(toolCalls(call("t1", "echo")), LlmReply.Text("done", usage))

        agent(hooks = listOf(first, second)).run("echo this")

        coVerify(exactly = 0) { second.beforeTool(any(), any(), any()) }
    }

    private fun namedTool(toolName: String, delayMs: Long, content: String) = mockk<Tool>().also {
        every { it.spec } returns ToolSpec(toolName, "Tool $toolName.", emptySchema)
        coEvery { it.run(any()) } coAnswers {
            delay(delayMs)
            ToolResult(content)
        }
    }

    @Test
    fun `given two tool calls in one turn when running then should run them in parallel`() = runTest {
        coEvery { echo.run(any()) } coAnswers {
            delay(1000)
            ToolResult("echo: hi")
        }
        repliesAre(toolCalls(call("t1", "echo"), call("t2", "echo")), LlmReply.Text("done", usage))

        agent().run("echo twice")

        assertEquals(1000, currentTime)
    }

    @Test
    fun `given two tool calls where the second finishes first when running then should send the results back in the order of the calls`() = runTest {
        val slow = namedTool("slow", 2000, "slow result")
        val fast = namedTool("fast", 1000, "fast result")
        repliesAre(toolCalls(call("t1", "slow"), call("t2", "fast")), LlmReply.Text("done", usage))

        agent(tools = listOf(slow, fast)).run("both")

        val results = requests[1].messages.filterIsInstance<Message.ToolResult>()
        assertEquals(listOf("t1", "t2"), results.map { it.callId })
        assertEquals(listOf("slow result", "fast result"), results.map { it.content })
        assertEquals(2000, currentTime)
    }

    @Test
    fun `given two tool calls where one throws when running then should still run the other and report the failure as an error result`() = runTest {
        val broken = mockk<Tool>().also {
            every { it.spec } returns ToolSpec("broken", "Always fails.", emptySchema)
            coEvery { it.run(any()) } throws IllegalStateException("tool exploded")
        }
        val fine = namedTool("fine", 500, "fine result")
        repliesAre(toolCalls(call("t1", "broken"), call("t2", "fine")), LlmReply.Text("done", usage))

        agent(tools = listOf(broken, fine)).run("both")

        val results = requests[1].messages.filterIsInstance<Message.ToolResult>()
        assertTrue(results[0].isError && "tool exploded" in results[0].content)
        assertEquals("fine result", results[1].content)
    }

    @Test
    fun `given a hook that throws for one call when running then should turn it into an error result for that call only`() = runTest {
        val broken = namedTool("broken", 0, "never used")
        val fine = namedTool("fine", 0, "fine result")
        val exploding = hook("Exploding").also {
            coEvery { it.beforeTool(any(), match { spec -> spec.name == "broken" }, any()) } throws IllegalStateException("guard exploded")
        }
        repliesAre(toolCalls(call("t1", "broken"), call("t2", "fine")), LlmReply.Text("done", usage))

        agent(tools = listOf(broken, fine), hooks = listOf(exploding)).run("both")

        val results = requests[1].messages.filterIsInstance<Message.ToolResult>()
        assertTrue(results[0].isError && "guard exploded" in results[0].content)
        assertEquals("fine result", results[1].content)
    }

    @Test
    fun `given several tool calls when running then should consult the hooks once per call`() = runTest {
        val counter = hook("Counter")
        repliesAre(toolCalls(call("t1", "echo"), call("t2", "echo"), call("t3", "echo")), LlmReply.Text("done", usage))

        agent(hooks = listOf(counter)).run("echo thrice")

        coVerify(exactly = 3) { counter.beforeTool(any(), any(), any()) }
        coVerify(exactly = 3) { counter.afterTool(any(), any(), any(), any()) }
    }
}
