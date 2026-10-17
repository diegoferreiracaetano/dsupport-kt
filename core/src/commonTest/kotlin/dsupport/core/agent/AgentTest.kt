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
        override val spec = ToolSpec("echo", "Devolve o texto recebido.", emptySchema)
        override suspend fun run(args: JsonObject) = ToolResult("eco: ${args["text"]}")
    }

    private fun call(id: String, tool: String) = ToolCall(id, tool, buildJsonObject { put("text", "oi") })

    @Test
    fun `devolve a resposta final quando o modelo responde texto`() = runTest {
        val llm = ScriptedLlmClient(listOf(LlmReply.Text("Olá!", usage)))
        val agent = Agent("atendimento", llm, "Seja cordial.", tools = emptyList())

        assertEquals("Olá!", agent.run("Oi"))
    }

    @Test
    fun `executa a ferramenta pedida e devolve o resultado ao modelo`() = runTest {
        val llm = ScriptedLlmClient(
            listOf(
                LlmReply.ToolCalls(listOf(call("t1", "echo")), usage),
                LlmReply.Text("pronto", usage),
            ),
        )
        val agent = Agent("atendimento", llm, "sys", tools = listOf(echoTool()))

        assertEquals("pronto", agent.run("faça eco"))

        val secondRequest = llm.received[1]
        val result = secondRequest.messages.filterIsInstance<Message.ToolResult>().single()
        assertEquals("t1", result.callId)
        assertEquals("echo", result.tool)
        assertTrue(result.content.startsWith("eco:"))
        assertEquals(listOf(result.content), agent.evidence)
    }

    @Test
    fun `ferramenta inexistente volta como erro sem derrubar o loop`() = runTest {
        val llm = ScriptedLlmClient(
            listOf(
                LlmReply.ToolCalls(listOf(call("t1", "cancel_order")), usage),
                LlmReply.Text("não consegui", usage),
            ),
        )
        val agent = Agent("atendimento", llm, "sys", tools = listOf(echoTool()))

        assertEquals("não consegui", agent.run("cancele"))

        val result = llm.received[1].messages.filterIsInstance<Message.ToolResult>().single()
        assertTrue(result.isError)
        assertTrue("cancel_order" in result.content)
    }

    @Test
    fun `para no limite de turnos`() = runTest {
        val looping = List(5) { LlmReply.ToolCalls(listOf(call("t$it", "echo")), usage) }
        val agent = Agent("atendimento", ScriptedLlmClient(looping), "sys", tools = listOf(echoTool()), maxTurns = 5)

        assertEquals("Limite de 5 turnos atingido. Encaminhando a um atendente.", agent.run("loop"))
    }

    @Test
    fun `emite os eventos na ordem`() = runTest {
        val events = mutableListOf<AgentEvent>()
        val llm = ScriptedLlmClient(
            listOf(
                LlmReply.ToolCalls(listOf(call("t1", "echo")), usage),
                LlmReply.Text("pronto", usage),
            ),
        )
        val agent = Agent("atendimento", llm, "sys", tools = listOf(echoTool()), events = EventSink { events += it })

        agent.run("faça eco")

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
