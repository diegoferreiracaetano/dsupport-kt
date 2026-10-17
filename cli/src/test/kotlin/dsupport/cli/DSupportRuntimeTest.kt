package dsupport.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DSupportRuntimeTest {
    private val calls = mutableListOf<String>()
    private val http = HttpClient(
        MockEngine { request ->
            calls += request.url.toString()
            respond("""{"content":[{"type":"text","text":"Hello!"}],"usage":{"input_tokens":1,"output_tokens":1}}""", HttpStatusCode.OK)
        },
    )
    private val settings = RuntimeSettings(
        apiKey = "test-key",
        modelId = "claude-haiku-5-5",
        dataDir = Path.of("..", "data"),
        agentsFile = Path.of("..", "config", "agents.json"),
        referenceDate = null,
    )

    private fun freshTicketsFile(): Path = createTempDirectory().resolve("tickets.json").also { it.writeText("[]") }

    @Test
    fun `given the project data and configuration when assembling then should build the assistant without calling the model`() = runTest {
        DSupportRuntime(settings, http).assemble(freshTicketsFile())

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `given a missing agents file when assembling then should fail`() = runTest {
        val broken = RuntimeSettings("k", "m", settings.dataDir, Path.of("no-such-file.json"), null)

        assertFailsWith<IOException> { DSupportRuntime(broken, http).assemble(freshTicketsFile()) }
    }

    @Test
    fun `given the assembled assistant when answering then should talk to the Anthropic messages endpoint`() = runTest {
        val answerCustomer = DSupportRuntime(settings, http).assemble(freshTicketsFile())

        runCatching { answerCustomer("Hi").toList() }

        assertTrue(calls.isNotEmpty() && calls.all { it.endsWith("/v1/messages") })
    }
}
