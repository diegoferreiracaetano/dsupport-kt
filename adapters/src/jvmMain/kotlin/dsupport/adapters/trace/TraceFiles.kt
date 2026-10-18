package dsupport.adapters.trace

import dsupport.contract.AgentEvent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Clock

/** Saves the events of a question as one JSON file, so executions can be compared later. */
class TraceFiles(private val now: () -> String = { Clock.System.now().toString() }) {
    private val json = Json { prettyPrint = true }

    fun save(dir: Path, title: String, events: List<AgentEvent>): Path {
        Files.createDirectories(dir)
        val file = dir.resolve("trace-${now().replace(':', '-')}.json")
        val body = buildJsonObject {
            put("title", title)
            put("events", json.encodeToJsonElement(ListSerializer(AgentEvent.serializer()), events))
        }
        Files.writeString(file, json.encodeToString(JsonObject.serializer(), body))
        return file
    }
}
