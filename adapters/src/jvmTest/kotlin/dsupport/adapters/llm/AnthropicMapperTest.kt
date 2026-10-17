package dsupport.adapters.llm

import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
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

class AnthropicMapperTest {
    private val mapper = AnthropicMapper()

    private val schema = buildJsonObject { put("type", "object") }
    private val echo = ToolSpec("echo", "Returns the text.", schema)
    private val args = buildJsonObject { put("text", "hi") }

    private fun body(
        messages: List<Message>,
        tools: List<ToolSpec> = emptyList(),
        output: OutputSchema? = null,
    ) = mapper.buildRequest("claude-haiku-5-5", 4096, LlmRequest("be brief", messages, tools), output)

    private fun JsonObject.messages() = getValue("messages").jsonArray.map { it.jsonObject }

    private fun JsonObject.blocks() = getValue("content").jsonArray.map { it.jsonObject }

    private fun JsonObject.type() = getValue("type").jsonPrimitive.content

    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `given a request with system, messages and tools when building then should map them to the API JSON`() {
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
    fun `given a tool result in the history when building then should send a tool_result with its tool_use_id in a user message`() {
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
    fun `given several results of the same turn when building then should send them in a single message`() {
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
    fun `given a user message after tool results when building then should merge them with the tool_result first`() {
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
    fun `given failed and successful results when building then should set is_error only on the failed one`() {
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
    fun `given no tools when building then should omit tools and never send tool_choice or sampling parameters`() {
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
    fun `given an output schema when building then should add output_config format with that schema`() {
        val output = OutputSchema("answer", "The final answer.", schema)

        val format = body(listOf(Message.User("hello")), output = output)
            .getValue("output_config").jsonObject.getValue("format").jsonObject

        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        assertEquals(schema, format.getValue("schema"))
    }

    @Test
    fun `given several tool_use blocks when parsing a reply then should return ToolCalls with the usage tokens`() {
        val reply = mapper.parseReply(
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
    fun `given several text blocks when parsing a reply then should join them into one Text reply`() {
        val reply = mapper.parseReply(
            parse("""{"content":[{"type":"text","text":"one"},{"type":"text","text":"two"}],"usage":{"input_tokens":1,"output_tokens":2}}"""),
        )

        assertEquals("one\ntwo", assertIs<LlmReply.Text>(reply).text)
    }

    @Test
    fun `given a JSON text when parsing a structured reply then should return the object`() {
        val result = mapper.parseStructured(
            parse("""{"content":[{"type":"text","text":"{\"answer\":\"ok\"}"}],"stop_reason":"end_turn","usage":{"input_tokens":7,"output_tokens":3}}"""),
        )

        assertEquals("ok", result.value.getValue("answer").jsonPrimitive.content)
        assertEquals(Usage(7, 3), result.usage)
    }

    @Test
    fun `given a body without content when parsing a reply then should throw LlmApiException`() {
        assertFailsWith<LlmApiException> { mapper.parseReply(parse("""{"stop_reason":"end_turn"}""")) }
    }

    @Test
    fun `given a thinking block when parsing a reply then should throw LlmApiException`() {
        val body = parse("""{"content":[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":"hi"}]}""")

        assertFailsWith<LlmApiException> { mapper.parseReply(body) }
    }

    @Test
    fun `given a refusal when parsing then should throw LlmApiException`() {
        val body = parse("""{"content":[],"stop_reason":"refusal"}""")

        assertFailsWith<LlmApiException> { mapper.parseReply(body) }
        assertFailsWith<LlmApiException> { mapper.parseStructured(body) }
    }

    @Test
    fun `given a cut or invalid JSON when parsing a structured reply then should throw LlmApiException`() {
        val cut = parse("""{"content":[{"type":"text","text":"{\"answer\":"}],"stop_reason":"max_tokens"}""")
        val invalid = parse("""{"content":[{"type":"text","text":"not json"}],"stop_reason":"end_turn"}""")

        assertFailsWith<LlmApiException> { mapper.parseStructured(cut) }
        assertFailsWith<LlmApiException> { mapper.parseStructured(invalid) }
    }

    @Test
    fun `given an empty content array when parsing a reply then should throw LlmApiException`() {
        val body = buildJsonObject { put("content", JsonArray(emptyList())) }

        assertFailsWith<LlmApiException> { mapper.parseReply(body) }
    }
}
