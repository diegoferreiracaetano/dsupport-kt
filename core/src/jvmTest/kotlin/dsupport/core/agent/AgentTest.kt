package dsupport.core.agent

import dsupport.contract.AgentEvent
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    private fun agent(tools: List<Tool> = listOf(echo), maxTurns: Int = 5) =
        Agent("support", llm, "Be friendly.", tools, sink, maxTurns)

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
        coEvery { llm.structured(capture(structured), schema) } returns expected
        val agent = agent()
        agent.run("question")

        val result = agent.finish(schema)

        assertEquals(expected, result)
        val messages = structured.single().messages
        assertEquals(Message.User("question"), messages.first())
        assertTrue("answer_format" in (messages.last() as Message.User).text)
    }
}
