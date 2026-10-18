package dsupport.cli

import dsupport.adapters.llm.AnthropicLlmClient
import dsupport.adapters.mcp.McpToolProvider
import dsupport.adapters.persistence.JsonOrderGateway
import dsupport.adapters.persistence.JsonTicketGateway
import dsupport.adapters.rag.KbLoader
import dsupport.adapters.rag.TfIdfEmbedder
import dsupport.adapters.rag.VectorKnowledgeBase
import dsupport.core.agent.AgentsConfig
import dsupport.core.mcp.McpServersConfig
import dsupport.core.tools.CreateTicketTool
import dsupport.core.tools.GetOrderStatusTool
import dsupport.core.tools.LocalToolProvider
import dsupport.core.tools.SearchKbTool
import dsupport.core.tools.TrackShipmentTool
import dsupport.core.usecase.AnswerCustomer
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/** Wires the real parts (model client, data files, tools) into an [AnswerCustomer]. Shared by the CLI and the evaluation. */
class DSupportRuntime(private val settings: RuntimeSettings, private val http: HttpClient) {

    suspend fun assemble(ticketsFile: Path): AnswerCustomer {
        val llm = AnthropicLlmClient(apiKey = settings.apiKey, modelId = settings.modelId, http = http)
        val knowledgeBase = VectorKnowledgeBase.create(TfIdfEmbedder(), KbLoader().load(settings.dataDir.resolve("kb")))
        val orders = JsonOrderGateway(settings.dataDir.resolve("orders.json"))
        val tickets = JsonTicketGateway(ticketsFile)
        val orderStatus = settings.referenceDate?.let { date -> GetOrderStatusTool(orders) { date } } ?: GetOrderStatusTool(orders)
        val providers = settings.mcpServersFile?.let { file ->
            val remote = McpToolProvider(Json.decodeFromString(McpServersConfig.serializer(), Files.readString(file)))
            Runtime.getRuntime().addShutdownHook(Thread { remote.close() })
            listOf(LocalToolProvider(listOf(SearchKbTool(knowledgeBase))), remote)
        } ?: listOf(
            LocalToolProvider(listOf(SearchKbTool(knowledgeBase), orderStatus, TrackShipmentTool(orders), CreateTicketTool(tickets))),
        )
        val agents = Json.decodeFromString(AgentsConfig.serializer(), Files.readString(settings.agentsFile))
        return DSupportAssembly(llm, providers, agents, tickets).answerCustomer()
    }
}
