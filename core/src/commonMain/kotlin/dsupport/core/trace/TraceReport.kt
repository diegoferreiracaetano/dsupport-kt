package dsupport.core.trace

import dsupport.contract.AgentEvent

/** Turns the events of one question into a readable tree, a cost table and a list of the slowest steps. */
class TraceReport {

    fun tree(events: List<AgentEvent>): String {
        val runs = LinkedHashMap<String, Run>()
        for (event in events) {
            when (event) {
                is AgentEvent.AgentStarted -> runOf(runs, event.runId, event.agent).also {
                    it.started = true
                    it.parent = event.parentRunId
                }
                is AgentEvent.ModelCalled -> runOf(runs, event.runId, event.agent).items += Item.Model(event)
                is AgentEvent.ToolStarted -> runOf(runs, event.runId, event.agent).items += Item.Tool(event.tool)
                is AgentEvent.ToolFinished -> runOf(runs, event.runId, event.agent).items
                    .filterIsInstance<Item.Tool>().firstOrNull { it.name == event.tool && it.finished == null }?.finished = event
                is AgentEvent.GuardActed -> runOf(runs, event.runId, event.agent).items += Item.Guard(event)
                else -> Unit
            }
        }
        val children = runs.values.filter { it.parent != null && it.parent in runs }.groupBy { it.parent }
        val roots = runs.values.filter { it.parent == null || it.parent !in runs }
        return roots.joinToString("\n") { render(it, 0, children) }
    }

    fun byAgent(events: List<AgentEvent>): List<AgentCost> =
        events.filterIsInstance<AgentEvent.ModelCalled>().groupBy { it.agent }.map { (agent, calls) ->
            AgentCost(agent, calls.size, calls.sumOf { it.inputTokens }, calls.sumOf { it.outputTokens }, calls.sumOf { it.durationMs })
        }

    fun slowest(events: List<AgentEvent>, n: Int = 3): List<AgentEvent> =
        events.filter { it is AgentEvent.ModelCalled || it is AgentEvent.ToolFinished }
            .sortedByDescending { if (it is AgentEvent.ModelCalled) it.durationMs else (it as AgentEvent.ToolFinished).durationMs }
            .take(n)

    private class Run(val id: String, val agent: String) {
        var started = false
        var parent: String? = null
        val items = mutableListOf<Item>()
    }

    private sealed interface Item {
        class Model(val event: AgentEvent.ModelCalled) : Item
        class Tool(val name: String, var finished: AgentEvent.ToolFinished? = null) : Item
        class Guard(val event: AgentEvent.GuardActed) : Item
    }

    private fun runOf(runs: MutableMap<String, Run>, id: String, agent: String): Run = runs.getOrPut(id) { Run(id, agent) }

    private fun render(run: Run, indent: Int, children: Map<String?, List<Run>>): String {
        val pad = " ".repeat(indent)
        val lines = mutableListOf(if (run.started) "$pad[${run.agent}] ${run.id}" else "$pad[${run.agent}]")
        val pending = children[run.id].orEmpty().toMutableList()
        for (item in run.items) {
            when (item) {
                is Item.Model -> lines += "$pad  model call: ${item.event.inputTokens} in / ${item.event.outputTokens} out tokens, ${item.event.durationMs} ms"
                is Item.Guard -> {
                    val target = item.event.tool?.let { "on $it" } ?: "on the answer"
                    lines += "$pad  guard ${item.event.guard} ${item.event.action} $target: ${item.event.detail}"
                }
                is Item.Tool -> {
                    lines += "$pad  tool ${item.name}: ${outcomeOf(item)}"
                    pending.indexOfFirst { it.agent == item.name }.takeIf { it >= 0 }?.let { index ->
                        lines += render(pending.removeAt(index), indent + 4, children)
                    }
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun outcomeOf(tool: Item.Tool): String {
        val finished = tool.finished ?: return "running"
        return if (finished.isError) "ERROR ${finished.durationMs} ms" else "${finished.durationMs} ms"
    }
}
