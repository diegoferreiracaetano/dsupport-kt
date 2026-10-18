package dsupport.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatRequest(val question: String)

/** What a client reads from the stream: an event of the run, or the failure that ended it. */
sealed interface StreamItem {
    data class Event(val event: AgentEvent) : StreamItem
    data class Failure(val message: String) : StreamItem
}

/** The wire format of `POST /chat`: one Server-Sent Events frame per [AgentEvent]. */
object ServerSentEvents {
    val json = Json { encodeDefaults = true }

    fun frame(event: AgentEvent): String = "data: ${json.encodeToString(AgentEvent.serializer(), event)}\n\n"

    fun errorFrame(message: String): String = "event: error\ndata: ${message.replace(Regex("\\s*[\\r\\n]+\\s*"), " ")}\n\n"
}

/** Reads the stream line by line. Returns an item when a frame is complete, otherwise null. */
class SseParser {
    private var failing = false

    fun accept(line: String): StreamItem? = when {
        line == "event: error" -> {
            failing = true
            null
        }
        line.startsWith("data: ") -> {
            val body = line.removePrefix("data: ")
            if (failing) {
                failing = false
                StreamItem.Failure(body)
            } else {
                StreamItem.Event(ServerSentEvents.json.decodeFromString(AgentEvent.serializer(), body))
            }
        }
        else -> null
    }
}
