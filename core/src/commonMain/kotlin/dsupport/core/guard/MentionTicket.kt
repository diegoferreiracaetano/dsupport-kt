package dsupport.core.guard

import dsupport.core.llm.Message

/** No silent actions: every ticket that showed up in the tool results must be named in the final answer. */
class MentionTicket : Hook {
    override suspend fun beforeFinal(agent: String, history: List<Message>, text: String): String {
        val created = history.filterIsInstance<Message.ToolResult>()
            .flatMap { result -> Patterns.ticketId.findAll(result.content).map { it.value }.toList() }
            .distinct()
        val missing = created.filter { it !in text }
        return if (missing.isEmpty()) text else "$text\n\nYour ticket number: ${missing.joinToString()}."
    }
}
