package dsupport.server

import dsupport.contract.AgentEvent
import dsupport.contract.ChatRequest
import dsupport.contract.ServerSentEvents
import dsupport.contract.SupportAnswer
import dsupport.core.usecase.AnswerCustomer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatModuleTest {

    private val started = AgentEvent.AgentStarted("orchestrator", 0, "hi", "r1")
    private val answer = SupportAnswer(answer = "Your order is on its way.", intent = "order", confidence = 0.9)

    private fun body(question: String) = ServerSentEvents.json.encodeToString(ChatRequest.serializer(), ChatRequest(question))

    @Test
    fun `given a question when posting to chat then should stream one frame per event as server-sent events`() = testApplication {
        val answerCustomer = mockk<AnswerCustomer>()
        every { answerCustomer("where is my order?") } returns flowOf(started, AgentEvent.Completed(answer))
        application { chatModule(answerCustomer) }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody(body("where is my order?"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.EventStream, response.contentType()?.withoutParameters())
        assertEquals(ServerSentEvents.frame(started) + ServerSentEvents.frame(AgentEvent.Completed(answer)), response.bodyAsText())
    }

    @Test
    fun `given a blank question when posting to chat then should answer 400 without running the assistant`() = testApplication {
        val answerCustomer = mockk<AnswerCustomer>(relaxed = true)
        application { chatModule(answerCustomer) }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody(body("   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        verify(exactly = 0) { answerCustomer(any()) }
    }

    @Test
    fun `given a body that is not a chat request when posting to chat then should answer 400`() = testApplication {
        application { chatModule(mockk(relaxed = true)) }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody("not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `given a question longer than the limit when posting to chat then should answer 400`() = testApplication {
        application { chatModule(mockk(relaxed = true)) }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody(body("x".repeat(MAX_QUESTION_LENGTH + 1)))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `given an assistant that fails midway when streaming then should end the stream with an error frame`() = testApplication {
        val answerCustomer = mockk<AnswerCustomer>()
        every { answerCustomer(any()) } returns flow {
            emit(started)
            throw IllegalStateException("model unavailable")
        }
        application { chatModule(answerCustomer) }

        val response = client.post("/chat") {
            contentType(ContentType.Application.Json)
            setBody(body("hello"))
        }

        assertEquals(ServerSentEvents.frame(started) + ServerSentEvents.errorFrame("model unavailable"), response.bodyAsText())
    }

    @Test
    fun `given a running server when asking for health then should answer ok`() = testApplication {
        application { chatModule(mockk(relaxed = true)) }

        val response = client.get("/health")

        assertEquals("ok", response.bodyAsText())
        assertTrue(response.status == HttpStatusCode.OK)
    }
}
