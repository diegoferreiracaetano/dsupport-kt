package dsupport.adapters.trace

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceFilesTest {
    private val files = TraceFiles { "2026-10-17T21:00:00Z" }
    private val events = listOf(
        AgentEvent.AgentStarted("orchestrator", 0, "question", runId = "r1"),
        AgentEvent.ModelCalled("orchestrator", 100, 10, 50, runId = "r1"),
        AgentEvent.Completed(SupportAnswer(answer = "Done.")),
    )

    @Test
    fun `given events when saving then should write a JSON file named from the timestamp inside the directory`() {
        val dir = createTempDirectory()

        val file = files.save(dir, "My order 1042", events)

        assertEquals(dir, file.parent)
        assertEquals("trace-2026-10-17T21-00-00Z.json", file.name)
        assertEquals("My order 1042", Json.parseToJsonElement(file.readText()).jsonObject.getValue("title").jsonPrimitive.content)
    }

    @Test
    fun `given a directory that does not exist when saving then should create it`() {
        val dir = createTempDirectory().resolve("nested").resolve("traces")

        val file = files.save(dir, "t", events)

        assertTrue(Files.exists(file))
    }

    @Test
    fun `given the saved file when decoding it then should give back the same events`() {
        val file = files.save(createTempDirectory(), "t", events)

        val saved = Json.parseToJsonElement(file.readText()).jsonObject.getValue("events").jsonArray
        val decoded = Json.decodeFromJsonElement(ListSerializer(AgentEvent.serializer()), saved)

        assertEquals(events, decoded)
    }

    @Test
    fun `given the saved file when reading it then should keep the run ids that rebuild the tree`() {
        val file = files.save(createTempDirectory(), "t", events)

        assertTrue("\"runId\": \"r1\"" in file.readText())
    }
}
