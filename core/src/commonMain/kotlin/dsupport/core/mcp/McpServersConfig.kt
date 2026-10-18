package dsupport.core.mcp

import kotlinx.serialization.Serializable

/** How to start one MCP server as a child process. */
@Serializable
data class McpServerConfig(val name: String, val command: List<String>, val env: Map<String, String> = emptyMap())

/** The MCP servers the agent connects to. Adding a server is a change here, never in the agent. */
@Serializable
data class McpServersConfig(val servers: List<McpServerConfig>)
