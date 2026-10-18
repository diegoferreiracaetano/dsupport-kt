package dsupport.cli

import dsupport.contract.AgentEvent
import dsupport.adapters.trace.TraceFiles
import dsupport.contract.SupportAnswer
import dsupport.core.trace.TraceReport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.system.exitProcess

private const val REQUEST_TIMEOUT_MS = 120_000L

private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

suspend fun main(args: Array<String>) {
    val question = args.joinToString(" ").trim()
    if (question.isEmpty()) {
        System.err.println("Usage: ./gradlew :cli:run --args=\"<customer question>\"")
        exitProcess(1)
    }
    val settings = try {
        SettingsReader().read(System.getenv())
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    }

    val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
    try {
        val answerCustomer = DSupportRuntime(settings, http).assemble(settings.dataDir.resolve("tickets.json"))
        val formatter = EventFormatter()
        val events = mutableListOf<AgentEvent>()
        answerCustomer(question).collect {
            events += it
            println(formatter.format(it))
        }

        val report = TraceReport()
        println("\nTrace:\n${report.tree(events)}")
        println("\n${formatter.formatCost(report.byAgent(events))}")
        println("\nTrace saved to ${TraceFiles().save(Path.of("traces"), question, events)}")
        val answer = events.filterIsInstance<AgentEvent.Completed>().last().answer
        println(prettyJson.encodeToString(SupportAnswer.serializer(), answer))
    } finally {
        http.close()
    }
}
