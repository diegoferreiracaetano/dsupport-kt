package dsupport.core.tools

import dsupport.core.tool.Tool
import dsupport.core.tool.ToolProvider

class LocalToolProvider(private val tools: List<Tool>) : ToolProvider {
    override suspend fun tools(): List<Tool> = tools
}
