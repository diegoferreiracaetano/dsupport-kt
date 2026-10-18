package dsupport.app

import dsupport.contract.ChatRequest
import dsupport.contract.ServerSentEvents
import dsupport.contract.SseParser
import dsupport.contract.StreamItem
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/** Where the answers come from. The app only knows this port. */
fun interface Assistant {
    fun ask(question: String): Flow<StreamItem>
}

/** Talks to the DSupport server: posts the question and reads the events as they stream back. */
class ChatClient(private val http: HttpClient, private val baseUrl: String) : Assistant {

    override fun ask(question: String): Flow<StreamItem> = channelFlow {
        val parser = SseParser()
        try {
            http.preparePost("$baseUrl/chat") {
                contentType(ContentType.Application.Json)
                setBody(ServerSentEvents.json.encodeToString(ChatRequest.serializer(), ChatRequest(question)))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    send(StreamItem.Failure(response.bodyAsText().ifBlank { response.status.toString() }))
                    return@execute
                }
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    parser.accept(line)?.let { send(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(StreamItem.Failure("could not reach the server at $baseUrl: ${e.message}"))
        }
    }
}
