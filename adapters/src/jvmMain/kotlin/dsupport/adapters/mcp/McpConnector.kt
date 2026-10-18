package dsupport.adapters.mcp

import dsupport.core.mcp.McpServerConfig

fun interface McpConnector {
    suspend fun connect(server: McpServerConfig): McpConnection
}
