package dsupport.adapters.llm

import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.OutputSchema
import dsupport.core.llm.StructuredReply
import dsupport.core.llm.Usage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnthropicLlmClientTest {
    private val request = LlmRequest("be brief", listOf(Message.User("hello")), emptyList())
    private val builtBody = buildJsonObject { put("built", "request") }
    private val okBody = """{"content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":3,"output_tokens":1}}"""
    private val reply = LlmReply.Text("hi", Usage(3, 1))
    private val mapper = mockk<AnthropicMapper>().also {
        every { it.buildRequest(any(), any(), any(), any()) } returns builtBody
        every { it.parseReply(any()) } returns reply
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(maxRetries: Int = 2, handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        AnthropicLlmClient(
            apiKey = "test-key",
            http = HttpClient(MockEngine { handler(it) }),
            maxRetries = maxRetries,
            mapper = mapper,
        )

    @Test
    fun `given a request when asking for the next reply then should post the built body with the required headers`() = runTest {
        var seen: HttpRequestData? = null

        val result = client { sent ->
            seen = sent
            json(HttpStatusCode.OK, okBody)
        }.next(request)

        val sent = seen!!
        assertEquals("/v1/messages", sent.url.encodedPath)
        assertEquals("test-key", sent.headers["x-api-key"])
        assertEquals("2023-06-01", sent.headers["anthropic-version"])
        assertEquals(builtBody.toString(), (sent.body as TextContent).text)
        assertEquals(reply, result)
        verify { mapper.buildRequest("claude-haiku-5-5", 4096, request, null) }
        verify { mapper.parseReply(match { it.containsKey("content") }) }
    }

    @Test
    fun `given an output schema when asking for a structured reply then should build the request with it and parse the result`() = runTest {
        val schema = OutputSchema("answer", "The final answer.", buildJsonObject { put("type", "object") })
        val parsed: JsonObject = buildJsonObject { put("answer", "ok") }
        every { mapper.parseStructured(any()) } returns StructuredReply(parsed, Usage(3, 1))
        every { mapper.buildRequest(any(), any(), any(), schema) } returns builtBody

        val result = client { json(HttpStatusCode.OK, okBody) }.structured(request, schema)

        assertEquals(StructuredReply(parsed, Usage(3, 1)), result)
        verify { mapper.buildRequest("claude-haiku-5-5", 4096, request, schema) }
    }

    @Test
    fun `given a 429 followed by a good response when asking for the next reply then should retry and return it`() = runTest {
        var calls = 0

        val result = client {
            calls++
            if (calls == 1) json(HttpStatusCode.TooManyRequests, """{"type":"error"}""") else json(HttpStatusCode.OK, okBody)
        }.next(request)

        assertEquals(2, calls)
        assertEquals(reply, result)
    }

    @Test
    fun `given a 529 overloaded response when asking for the next reply then should retry like any 5xx`() = runTest {
        var calls = 0

        client {
            calls++
            if (calls == 1) json(HttpStatusCode(529, "Overloaded"), "{}") else json(HttpStatusCode.OK, okBody)
        }.next(request)

        assertEquals(2, calls)
    }

    @Test
    fun `given a persistent 500 when asking for the next reply then should retry up to maxRetries and throw`() = runTest {
        var calls = 0

        val failure = assertFailsWith<LlmApiException> {
            client(maxRetries = 2) {
                calls++
                json(HttpStatusCode.InternalServerError, """{"type":"error"}""")
            }.next(request)
        }

        assertEquals(3, calls)
        assertEquals(500, failure.status)
    }

    @Test
    fun `given a 401 when asking for the next reply then should throw immediately without retrying`() = runTest {
        var calls = 0

        val failure = assertFailsWith<LlmApiException> {
            client {
                calls++
                json(HttpStatusCode.Unauthorized, """{"error":{"message":"invalid x-api-key"}}""")
            }.next(request)
        }

        assertEquals(1, calls)
        assertEquals(401, failure.status)
    }

    @Test
    fun `given a body that is not JSON when asking for the next reply then should throw LlmApiException`() = runTest {
        assertFailsWith<LlmApiException> {
            client { json(HttpStatusCode.OK, "<html>oops</html>") }.next(request)
        }
    }
}
