package dsupport.core.agent

import dsupport.core.tool.Tool
import dsupport.core.tool.ToolProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All the tools of all providers, loaded once, from which each agent gets only the ones its configuration lists. */
class ToolCatalog(private val providers: List<ToolProvider>) {
    private val lock = Mutex()
    private var loaded: Map<String, Tool>? = null

    suspend fun select(names: List<String>): List<Tool> {
        val available = available()
        return names.map { available[it] ?: throw IllegalArgumentException("unknown tool: $it") }
    }

    private suspend fun available(): Map<String, Tool> = lock.withLock {
        loaded ?: providers.flatMap { it.tools() }.let { tools ->
            tools.groupBy { it.spec.name }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
                throw IllegalStateException("tool '$it' is provided by more than one provider")
            }
            tools.associateBy { it.spec.name }.also { loaded = it }
        }
    }
}
