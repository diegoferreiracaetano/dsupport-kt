package dsupport.adapters.llm

import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.ToolCall
import dsupport.core.tool.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnthropicMappingTest {
    private val schema = buildJsonObject { put("type", "object") }
    private val echo = ToolSpec("echo", "Returns the text.", schema)
    private val args = buildJsonObject { put("text", "hi") }

    private fun body(
        messages: List<Message>,
        tools: List<ToolSpec> = emptyList(),
        output: OutputSchema? = null,
    ) = buildRequestBody("claude-haiku-5-5", 4096, LlmRequest("be brief", messages, tools), output)

    private fun JsonObject.messages() = getValue("messages").jsonArray.map { it.jsonObject }

    private fun JsonObject.blocks() = getValue("content").jsonArray.map { it.jsonObject }

    private fun JsonObject.type() = getValue("type").jsonPrimitive.content

    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `maps system, messages and tools to the API JSON`() {
        val json = body(listOf(Message.User("hello")), listOf(echo))

        assertEquals("claude-haiku-5-5", json.getValue("model").jsonPrimitive.content)
        assertEquals(4096, json.getValue("max_tokens").jsonPrimitive.int)
        assertEquals("be brief", json.getValue("system").jsonPrimitive.content)
        assertEquals("disabled", json.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        val tool = json.getValue("tools").jsonArray.single().jsonObject
        assertEquals("echo", tool.getValue("name").jsonPrimitive.content)
        assertEquals(schema, tool.getValue("input_schema"))
        val message = json.messages().single()
        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertEquals("hello", message.blocks().single().getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `tool result becomes a tool_result with tool_use_id inside a user message`() {
        val json = body(
            listOf(
                Message.User("q"),
                Message.AssistantToolCalls(listOf(ToolCall("t1", "echo", args))),
                Message.ToolResult("t1", "echo", "echo: hi", isError = false),
            ),
            listOf(echo),
        )

        val messages = json.messages()
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.getValue("role").jsonPrimitive.content })
        val use = messages[1].blocks().single()
        assertEquals("tool_use", use.type())
        assertEquals("t1", use.getValue("id").jsonPrimitive.content)
        assertEquals("echo", use.getValue("name").jsonPrimitive.content)
        assertEquals(args, use.getValue("input"))
        val result = messages[2].blocks().single()
        assertEquals("tool_result", result.type())
        assertEquals("t1", result.getValue("tool_use_id").jsonPrimitive.content)
        assertEquals("echo: hi", result.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `several results of the same turn go in a single message`() {
        val json = body(
            listOf(
                Message.User("q"),
                Message.AssistantToolCalls(listOf(ToolCall("t1", "echo", args), ToolCall("t2", "echo", args))),
                Message.ToolResult("t1", "echo", "one", isError = false),
                Message.ToolResult("t2", "echo", "two", isError = false),
            ),
            listOf(echo),
        )

        val messages = json.messages()
        assertEquals(3, messages.size)
        assertEquals(listOf("tool_result", "tool_result"), messages.last().blocks().map { it.type() })
    }

    @Test
    fun `consecutive user messages become one, with tool_result before the text`() {
        val json = body(
            listOf(
                Message.User("q"),
                Message.AssistantToolCalls(listOf(ToolCall("t1", "echo", args))),
                Message.ToolResult("t1", "echo", "one", isError = false),
                Message.User("now answer"),
            ),
            listOf(echo),
        )

        val last = json.messages().last()
        assertEquals(3, json.messages().size)
        assertEquals(listOf("tool_result", "text"), last.blocks().map { it.type() })
    }

    @Test
    fun `is_error is set on the tool_result only when the tool failed`() {
        val json = body(
            listOf(
                Message.User("q"),
                Message.AssistantToolCalls(listOf(ToolCall("t1", "echo", args), ToolCall("t2", "echo", args))),
                Message.ToolResult("t1", "echo", "boom", isError = true),
                Message.ToolResult("t2", "echo", "fine", isError = false),
            ),
            listOf(echo),
        )

        val results = json.messages().last().blocks()
        assertTrue(results[0].getValue("is_error").jsonPrimitive.boolean)
        assertNull(results[1]["is_error"])
    }

    @Test
    fun `omits tools when there are none and never sends tool_choice or sampling parameters`() {
        val withoutTools = body(listOf(Message.User("hello")))
        val withTools = body(listOf(Message.User("hello")), listOf(echo))

        assertFalse("tools" in withoutTools)
        for (json in listOf(withoutTools, withTools)) {
            assertFalse("tool_choice" in json)
            assertFalse("temperature" in json)
            assertFalse("top_p" in json)
        }
    }

    @Test
    fun `structured request carries output_config format with the schema`() {
        val output = OutputSchema("answer", "The final answer.", schema)

        val format = body(listOf(Message.User("hello")), output = output)
            .getValue("output_config").jsonObject.getValue("format").jsonObject

        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        assertEquals(schema, format.getValue("schema"))
    }

    @Test
    fun `reads several tool_use blocks as ToolCalls with the usage tokens`() {
        val reply = parseReply(
            parse(
                """{"content":[{"type":"text","text":"checking"},
                    {"type":"tool_use","id":"t1","name":"echo","input":{"text":"a"}},
                    {"type":"tool_use","id":"t2","name":"echo","input":{"text":"b"}}],
                    "stop_reason":"tool_use","usage":{"input_tokens":312,"output_tokens":54}}""",
            ),
        )

        val calls = assertIs<LlmReply.ToolCalls>(reply)
        assertEquals(listOf("t1", "t2"), calls.calls.map { it.id })
        assertEquals("b", calls.calls[1].args.getValue("text").jsonPrimitive.content)
        assertEquals(312, reply.usage.inputTokens)
        assertEquals(54, reply.usage.outputTokens)
    }

    @Test
    fun `joins text blocks into a single Text reply`() {
        val reply = parseReply(
            parse("""{"content":[{"type":"text","text":"one"},{"type":"text","text":"two"}],"usage":{"input_tokens":1,"output_tokens":2}}"""),
        )

        assertEquals("one\ntwo", assertIs<LlmReply.Text>(reply).text)
    }

    @Test
    fun `structured reply reads the JSON object from the text`() {
        val result = parseStructured(
            parse("""{"content":[{"type":"text","text":"{\"answer\":\"ok\"}"}],"stop_reason":"end_turn"}"""),
        )

        assertEquals("ok", result.getValue("answer").jsonPrimitive.content)
    }

    @Test
    fun `body without content throws LlmApiException`() {
        assertFailsWith<LlmApiException> { parseReply(parse("""{"stop_reason":"end_turn"}""")) }
    }

    @Test
    fun `thinking block throws LlmApiException`() {
        val body = parse("""{"content":[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":"hi"}]}""")

        assertFailsWith<LlmApiException> { parseReply(body) }
    }

    @Test
    fun `refusal throws LlmApiException`() {
        val body = parse("""{"content":[],"stop_reason":"refusal"}""")

        assertFailsWith<LlmApiException> { parseReply(body) }
        assertFailsWith<LlmApiException> { parseStructured(body) }
    }

    @Test
    fun `structured reply cut by max_tokens or with invalid JSON throws LlmApiException`() {
        val cut = parse("""{"content":[{"type":"text","text":"{\"answer\":"}],"stop_reason":"max_tokens"}""")
        val invalid = parse("""{"content":[{"type":"text","text":"not json"}],"stop_reason":"end_turn"}""")

        assertFailsWith<LlmApiException> { parseStructured(cut) }
        assertFailsWith<LlmApiException> { parseStructured(invalid) }
    }

    @Test
    fun `an empty JsonArray is not valid content for a reply`() {
        val body = buildJsonObject { put("content", JsonArray(emptyList())) }

        assertFailsWith<LlmApiException> { parseReply(body) }
    }
}
