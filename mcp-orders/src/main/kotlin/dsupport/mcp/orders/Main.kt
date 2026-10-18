package dsupport.mcp.orders

import dsupport.adapters.mcp.McpToolServer
import dsupport.adapters.persistence.JsonOrderGateway
import dsupport.core.tools.GetOrderStatusTool
import dsupport.core.tools.TrackShipmentTool
import kotlinx.datetime.LocalDate
import java.nio.file.Path

suspend fun main() {
    val orders = JsonOrderGateway(Path.of(System.getenv("DATA_DIR") ?: "data").resolve("orders.json"))
    val status = System.getenv("REFERENCE_DATE")?.let { date -> GetOrderStatusTool(orders) { LocalDate.parse(date) } } ?: GetOrderStatusTool(orders)
    McpToolServer("dsupport-orders", "1", listOf(status, TrackShipmentTool(orders))).serve()
}
