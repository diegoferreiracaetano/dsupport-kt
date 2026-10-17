package dsupport.testing

import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Suíte de contrato: qualquer LlmClient (o roteirizado, o da Anthropic, um futuro) tem que passar nela.
 * Quem estende diz como montar o cliente para cada [Scenario].
 */
abstract class LlmClientContract {
    abstract fun clientFor(scenario: Scenario): LlmClient

    private val request = LlmRequest(
        system = "Você é um assistente.",
        messages = listOf(Message.User("Cadê meu pedido 1042?")),
        tools = emptyList(),
    )
    private val schema = OutputSchema("responder", "Resposta final.", buildJsonObject { put("type", "object") })

    @Test
    fun `texto vira LlmReply Text`() = runTest {
        val reply = clientFor(Scenario.Text("Seu pedido chega amanhã.")).next(request)

        assertEquals("Seu pedido chega amanhã.", assertIs<LlmReply.Text>(reply).text)
    }

    @Test
    fun `pedido de ferramenta preserva id nome e argumentos`() = runTest {
        val args = buildJsonObject { put("order_id", "1042") }
        val reply = clientFor(Scenario.ToolCall("call_1", "get_order_status", args)).next(request)

        val call = assertIs<LlmReply.ToolCalls>(reply).calls.single()
        assertEquals("call_1", call.id)
        assertEquals("get_order_status", call.tool)
        assertEquals(args, call.args)
    }

    @Test
    fun `saida estruturada devolve o objeto`() = runTest {
        val expected: JsonObject = buildJsonObject { put("answer", "ok") }

        assertEquals(expected, clientFor(Scenario.Structured(expected)).structured(request, schema))
    }
}
