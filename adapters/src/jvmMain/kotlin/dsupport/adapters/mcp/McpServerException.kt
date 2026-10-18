package dsupport.adapters.mcp

/** A problem with one MCP server: it did not start, did not answer, or clashes with another server. */
class McpServerException(val server: String, message: String, cause: Throwable? = null) : Exception(message, cause)
