package dsupport.core.answer

import dsupport.contract.INTENTS
import dsupport.contract.SupportAnswer

private const val MIN_UNESCALATED_CONFIDENCE = 0.5

/** Checks with plain code what can be checked without a model. [evidence] are the tool results of the conversation. */
class SupportAnswerValidator {

    fun problems(answer: SupportAnswer, evidence: List<String>): List<String> = buildList {
        val results = evidence.joinToString("\n")
        if (answer.answer.isBlank()) add("answer is empty")
        if (answer.intent !in INTENTS) add("invalid intent: ${answer.intent}")
        if (answer.confidence !in 0.0..1.0) add("confidence out of range 0..1: ${answer.confidence}")
        if (answer.escalated && answer.ticketId == null) add("escalated=true without ticket_id")
        answer.ticketId?.let { ticket ->
            if (ticket !in results) add("ticket $ticket does not appear in the tool results (invented?)")
            if (ticket !in answer.answer) add("ticket $ticket is missing from the customer-facing text")
        }
        if (!answer.escalated && answer.confidence < MIN_UNESCALATED_CONFIDENCE) {
            add("confidence ${answer.confidence} without escalating: escalate or answer only what the tool results support")
        }
        answer.sources.filter { !results.contains(it, ignoreCase = true) }
            .forEach { add("source \"$it\" does not appear in the tool results") }
    }
}
