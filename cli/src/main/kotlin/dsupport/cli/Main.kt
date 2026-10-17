package dsupport.cli

import dsupport.adapters.llm.AnthropicLlmClient
import dsupport.adapters.persistence.JsonOrderGateway
import dsupport.adapters.persistence.JsonTicketGateway
import dsupport.adapters.rag.KbLoader
import dsupport.adapters.rag.TfIdfEmbedder
import dsupport.adapters.rag.VectorKnowledgeBase
import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import dsupport.core.agent.AgentsConfig
import dsupport.core.tools.CreateTicketTool
import dsupport.core.tools.GetOrderStatusTool
import dsupport.core.tools.LocalToolProvider
import dsupport.core.tools.SearchKbTool
import dsupport.core.tools.TrackShipmentTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.nio.file.Files
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
    val apiKey = System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }
    if (apiKey == null) {
        System.err.println("Set ANTHROPIC_API_KEY (see .env.example).")
        exitProcess(1)
    }
    val dataDir = Path.of(System.getenv("DATA_DIR") ?: "data")
    val referenceDate = System.getenv("REFERENCE_DATE")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }

    val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
    try {
        val llm = AnthropicLlmClient(
            apiKey = apiKey,
            modelId = System.getenv("ANTHROPIC_MODEL") ?: "claude-haiku-5-5",
            http = http,
        )
        val knowledgeBase = VectorKnowledgeBase.create(TfIdfEmbedder(), KbLoader().load(dataDir.resolve("kb")))
        val orders = JsonOrderGateway(dataDir.resolve("orders.json"))
        val tickets = JsonTicketGateway(dataDir.resolve("tickets.json"))
        val orderStatus = referenceDate?.let { date -> GetOrderStatusTool(orders) { date } } ?: GetOrderStatusTool(orders)
        val tools = LocalToolProvider(
            listOf(SearchKbTool(knowledgeBase), orderStatus, TrackShipmentTool(orders), CreateTicketTool(tickets)),
        )
        val agents = Json.decodeFromString(AgentsConfig.serializer(), Files.readString(Path.of("config", "agents.json")))
        val assembly = DSupportAssembly(llm, listOf(tools), agents, tickets)

        val formatter = EventFormatter()
        val events = mutableListOf<AgentEvent>()
        assembly.answerCustomer()(question).collect {
            events += it
            println(formatter.format(it))
        }

        val calls = events.filterIsInstance<AgentEvent.ModelCalled>()
        println("\nTotal: ${calls.size} model calls, ${calls.sumOf { it.inputTokens }} in / ${calls.sumOf { it.outputTokens }} out tokens")
        val answer = events.filterIsInstance<AgentEvent.Completed>().last().answer
        println(prettyJson.encodeToString(SupportAnswer.serializer(), answer))
    } finally {
        http.close()
    }
}
