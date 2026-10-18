package dsupport.core.trace

data class AgentCost(val agent: String, val calls: Int, val inputTokens: Int, val outputTokens: Int, val modelMs: Long)
