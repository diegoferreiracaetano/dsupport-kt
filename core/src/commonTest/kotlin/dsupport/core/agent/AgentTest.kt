package dsupport.core.agent

import dsupport.contract.AgentEvent
import dsupport.core.llm.LlmReply
import dsupport.core.llm.Message
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import dsupport.testing.ScriptedLlmClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentTest {
    private val usage = Usage(inputTokens = 10, outputTokens = 5)
    private val emptySchema = buildJsonObject { put("type", "object") }

    private fun echoTool() = object : Tool {
        override val spec = ToolSpec("echo", "Returns the received text.", emptySchema)
        override suspend fun run(args: JsonObject) = ToolResult("echo: ${args["text"]}")
    }

    private fun call(id: String, tool: String) = ToolCall(id, tool, buildJsonObject { put("text", "hi") })

    @Test
    fun `returns the final answer when the model replies with text`() = runTest {
        val llm = ScriptedLlmClient(listOf(LlmReply.Text("Hello!", usage)))
        val agent = Agent("support", llm, "Be friendly.", tools = emptyList())

        assertEquals("Hello!", agent.run("Hi"))
    }

    @Test
    fun `runs the requested tool and sends the result back to the model`() = runTest {
        val llm = ScriptedLlmClient(
            listOf(
                LlmReply.ToolCalls(listOf(call("t1", "echo")), usage),
                LlmReply.Text("done", usage),
            ),
        )
        val agent = Agent("support", llm, "sys", tools = listOf(echoTool()))

        assertEquals("done", agent.run("echo this"))

        val secondRequest = llm.received[1]
        val result = secondRequest.messages.filterIsInstance<Message.ToolResult>().single()
        assertEquals("t1", result.callId)
        assertEquals("echo", result.tool)
        assertTrue(result.content.startsWith("echo:"))
        assertEquals(listOf(result.content), agent.evidence)
    }

    @Test
    fun `unknown tool comes back as an error without breaking the loop`() = runTest {
        val llm = ScriptedLlmClient(
            listOf(
                LlmReply.ToolCalls(listOf(call("t1", "cancel_order")), usage),
                LlmReply.Text("could not do it", usage),
            ),
        )
        val agent = Agent("support", llm, "sys", tools = listOf(echoTool()))

        assertEquals("could not do it", agent.run("cancel it"))

        val result = llm.received[1].messages.filterIsInstance<Message.ToolResult>().single()
        assertTrue(result.isError)
        assertTrue("cancel_order" in result.content)
    }

    @Test
    fun `stops at the turn limit`() = runTest {
        val looping = List(5) { LlmReply.ToolCalls(listOf(call("t$it", "echo")), usage) }
        val agent = Agent("support", ScriptedLlmClient(looping), "sys", tools = listOf(echoTool()), maxTurns = 5)

        assertEquals("Turn limit of 5 reached. Escalating to a human agent.", agent.run("loop forever"))
    }

    @Test
    fun `emits events in order`() = runTest {
        val events = mutableListOf<AgentEvent>()
        val llm = ScriptedLlmClient(
            listOf(
                LlmReply.ToolCalls(listOf(call("t1", "echo")), usage),
                LlmReply.Text("done", usage),
            ),
        )
        val agent = Agent("support", llm, "sys", tools = listOf(echoTool()), events = EventSink { events += it })

        agent.run("echo this")

        assertEquals(
            listOf("AgentStarted", "ModelCalled", "ToolStarted", "ToolFinished", "ModelCalled"),
            events.map { it::class.simpleName },
        )
        val model = events.filterIsInstance<AgentEvent.ModelCalled>().first()
        assertEquals(10, model.inputTokens)
        assertEquals(5, model.outputTokens)
        assertIs<AgentEvent.AgentStarted>(events.first())
    }
}
