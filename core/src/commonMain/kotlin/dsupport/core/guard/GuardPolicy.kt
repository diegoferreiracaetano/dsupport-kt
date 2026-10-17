package dsupport.core.guard

import dsupport.core.tool.Tool

/** Checked when the agents are assembled: a tool marked destructive must be covered by at least one guard. */
class GuardPolicy {
    fun validate(tools: List<Tool>, hooks: List<Hook>) {
        val covered = hooks.flatMap { it.covers }.toSet()
        tools.map { it.spec }.firstOrNull { it.destructive && it.name !in covered }?.let {
            throw IllegalStateException("destructive tool '${it.name}' has no guard")
        }
    }
}
