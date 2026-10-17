package dsupport.core.agent

import dsupport.contract.AgentEvent

fun interface EventSink {
    suspend fun emit(event: AgentEvent)

    companion object {
        val None = EventSink { }
    }
}
