package dsupport.core.agent

import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import dsupport.core.tools.fail
import dsupport.core.tools.stringArg
import dsupport.core.tools.stringSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * A tool whose implementation is another agent. Each call gets a fresh agent from [newAgent],
 * because an [Agent] keeps its history and concurrent runs must not share it.
 */
class SubAgentTool(
    private val name: String,
    description: String,
    private val newAgent: () -> Agent,
) : Tool {

    override val spec = ToolSpec(name, description, stringSchema("task"))

    override suspend fun run(args: JsonObject): ToolResult {
        val task = args.stringArg("task") ?: return fail("missing_argument:task")
        return try {
            ToolResult(newAgent().run(task))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult("error: specialist '$name' failed: ${e.message}", isError = true)
        }
    }
}

fun subAgentTool(name: String, description: String, newAgent: () -> Agent): Tool = SubAgentTool(name, description, newAgent)
