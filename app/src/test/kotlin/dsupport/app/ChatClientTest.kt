package dsupport.app

import dsupport.contract.AgentEvent
import dsupport.contract.ChatRequest
import dsupport.contract.ServerSentEvents
import dsupport.contract.StreamItem
import dsupport.contract.SupportAnswer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatClientTest {

    private val started = AgentEvent.AgentStarted("orchestrator", 0, "hi", "r1")
    private val completed = AgentEvent.Completed(SupportAnswer(answer = "Hello!", intent = "other", confidence = 0.8))
    private val streamHeaders = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())

    private fun clientAnswering(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        ChatClient(HttpClient(MockEngine(handler)), "http://server:8080")

    @Test
    fun `given a streamed body when asking then should emit the events in order`() = runTest {
        val body = ServerSentEvents.frame(started) + ServerSentEvents.frame(completed)
        val client = clientAnswering { respond(body, HttpStatusCode.OK, streamHeaders) }

        val items = client.ask("hi").toList()

        assertEquals(listOf(StreamItem.Event(started), StreamItem.Event(completed)), items)
    }

    @Test
    fun `given a question when asking then should post the request as JSON to the chat route`() = runTest {
        var method: HttpMethod? = null
        var url = ""
        var sent = ""
        val client = clientAnswering { request ->
            method = request.method
            url = request.url.toString()
            sent = String(request.body.toByteArray())
            respond("", HttpStatusCode.OK, streamHeaders)
        }

        client.ask("where is my order?").toList()

        assertEquals(HttpMethod.Post, method)
        assertEquals("http://server:8080/chat", url)
        assertEquals(ServerSentEvents.json.encodeToString(ChatRequest.serializer(), ChatRequest("where is my order?")), sent)
    }

    @Test
    fun `given a stream that ends with an error frame when asking then should emit the failure`() = runTest {
        val body = ServerSentEvents.frame(started) + ServerSentEvents.errorFrame("model unavailable")
        val client = clientAnswering { respond(body, HttpStatusCode.OK, streamHeaders) }

        val items = client.ask("hi").toList()

        assertEquals(listOf(StreamItem.Event(started), StreamItem.Failure("model unavailable")), items)
    }

    @Test
    fun `given a rejected question when asking then should emit the server message as a failure`() = runTest {
        val client = clientAnswering { respond("the question must have between 1 and 2000 characters", HttpStatusCode.BadRequest) }

        val items = client.ask("hi").toList()

        assertEquals(listOf(StreamItem.Failure("the question must have between 1 and 2000 characters")), items)
    }

    @Test
    fun `given a server that cannot be reached when asking then should emit a failure naming the address`() = runTest {
        val client = clientAnswering { throw IOException("connection refused") }

        val items = client.ask("hi").toList()

        val failure = items.single() as StreamItem.Failure
        assertTrue("http://server:8080" in failure.message, failure.message)
    }
}
