package dsupport.adapters.llm

import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AnthropicLlmClientTest {
    private val request = LlmRequest("be brief", listOf(Message.User("hello")), emptyList())
    private val okBody = """{"content":[{"type":"text","text":"hi"}],"stop_reason":"end_turn","usage":{"input_tokens":3,"output_tokens":1}}"""

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(maxRetries: Int = 2, handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        AnthropicLlmClient(apiKey = "test-key", http = HttpClient(MockEngine { handler(it) }), maxRetries = maxRetries)

    @Test
    fun `posts to the messages endpoint with the required headers`() = runTest {
        var seen: HttpRequestData? = null
        val reply = client { request ->
            seen = request
            json(HttpStatusCode.OK, okBody)
        }.next(request)

        val sent = seen!!
        assertEquals("/v1/messages", sent.url.encodedPath)
        assertEquals("test-key", sent.headers["x-api-key"])
        assertEquals("2023-06-01", sent.headers["anthropic-version"])
        assertEquals("hi", assertIs<LlmReply.Text>(reply).text)
    }

    @Test
    fun `429 is retried and the later good response is returned`() = runTest {
        var calls = 0
        val reply = client {
            calls++
            if (calls == 1) json(HttpStatusCode.TooManyRequests, """{"type":"error"}""") else json(HttpStatusCode.OK, okBody)
        }.next(request)

        assertEquals(2, calls)
        assertEquals("hi", assertIs<LlmReply.Text>(reply).text)
    }

    @Test
    fun `529 overloaded is retried like any 5xx`() = runTest {
        var calls = 0
        client {
            calls++
            if (calls == 1) json(HttpStatusCode(529, "Overloaded"), "{}") else json(HttpStatusCode.OK, okBody)
        }.next(request)

        assertEquals(2, calls)
    }

    @Test
    fun `500 is retried up to maxRetries and then throws LlmApiException`() = runTest {
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
    fun `401 throws immediately without retrying`() = runTest {
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
    fun `a body that is not JSON throws LlmApiException`() = runTest {
        assertFailsWith<LlmApiException> { client { json(HttpStatusCode.OK, "<html>oops</html>") }.next(request) }
    }
}
