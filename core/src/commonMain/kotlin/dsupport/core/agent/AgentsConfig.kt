package dsupport.core.agent

import kotlinx.serialization.Serializable

@Serializable
data class AgentDef(val tools: List<String>, val maxTurns: Int = 4)

/** Which tools each agent may use. Anything not listed is simply not there for that agent. */
@Serializable
data class AgentsConfig(val agents: Map<String, AgentDef>)
