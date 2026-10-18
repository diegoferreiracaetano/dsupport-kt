package dsupport.mcp.tickets

import dsupport.adapters.mcp.McpToolServer
import dsupport.adapters.persistence.JsonTicketGateway
import dsupport.core.tools.CreateTicketTool
import java.nio.file.Path

suspend fun main() {
    val tickets = JsonTicketGateway(Path.of(System.getenv("DATA_DIR") ?: "data").resolve("tickets.json"))
    McpToolServer("dsupport-tickets", "1", listOf(CreateTicketTool(tickets))).serve()
}
