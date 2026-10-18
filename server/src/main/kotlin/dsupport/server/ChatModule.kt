package dsupport.server

import dsupport.contract.ChatRequest
import dsupport.contract.ServerSentEvents
import dsupport.core.usecase.AnswerCustomer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.SerializationException

const val MAX_QUESTION_LENGTH = 2_000

/** Exposes the assistant over HTTP: `POST /chat` streams the events of one question as Server-Sent Events. */
fun Application.chatModule(answerCustomer: AnswerCustomer) {
    routing {
        get("/health") { call.respondText("ok") }
        post("/chat") {
            val question = try {
                ServerSentEvents.json.decodeFromString(ChatRequest.serializer(), call.receiveText()).question.trim()
            } catch (e: SerializationException) {
                return@post call.respondText("expected {\"question\": \"...\"}", status = HttpStatusCode.BadRequest)
            } catch (e: IllegalArgumentException) {
                return@post call.respondText("expected {\"question\": \"...\"}", status = HttpStatusCode.BadRequest)
            }
            if (question.isEmpty() || question.length > MAX_QUESTION_LENGTH) {
                return@post call.respondText(
                    "the question must have between 1 and $MAX_QUESTION_LENGTH characters",
                    status = HttpStatusCode.BadRequest,
                )
            }
            call.respondTextWriter(ContentType.Text.EventStream) {
                answerCustomer(question)
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        write(ServerSentEvents.errorFrame(failure.message ?: failure::class.simpleName.orEmpty()))
                        flush()
                    }
                    .collect { event ->
                        write(ServerSentEvents.frame(event))
                        flush()
                    }
            }
        }
    }
}
