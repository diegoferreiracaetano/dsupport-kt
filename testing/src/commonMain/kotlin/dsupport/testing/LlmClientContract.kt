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

/** Contract suite every LlmClient must pass. Subclasses build the client for each [Scenario]. */
abstract class LlmClientContract {
    abstract fun clientFor(scenario: Scenario): LlmClient

    private val request = LlmRequest(
        system = "You are an assistant.",
        messages = listOf(Message.User("Where is my order 1042?")),
        tools = emptyList(),
    )
    private val schema = OutputSchema("responder", "Final answer.", buildJsonObject { put("type", "object") })

    @Test
    fun `text becomes LlmReply Text`() = runTest {
        val reply = clientFor(Scenario.Text("Your order arrives tomorrow.")).next(request)

        assertEquals("Your order arrives tomorrow.", assertIs<LlmReply.Text>(reply).text)
    }

    @Test
    fun `tool request keeps id name and arguments`() = runTest {
        val args = buildJsonObject { put("order_id", "1042") }
        val reply = clientFor(Scenario.ToolCall("call_1", "get_order_status", args)).next(request)

        val call = assertIs<LlmReply.ToolCalls>(reply).calls.single()
        assertEquals("call_1", call.id)
        assertEquals("get_order_status", call.tool)
        assertEquals(args, call.args)
    }

    @Test
    fun `structured output returns the object`() = runTest {
        val expected: JsonObject = buildJsonObject { put("answer", "ok") }

        assertEquals(expected, clientFor(Scenario.Structured(expected)).structured(request, schema))
    }
}
