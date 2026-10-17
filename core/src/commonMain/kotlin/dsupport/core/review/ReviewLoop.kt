package dsupport.core.review

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import dsupport.core.agent.Agent
import dsupport.core.agent.EventSink
import dsupport.core.answer.SupportAnswerSchema
import dsupport.core.answer.SupportAnswerValidator
import dsupport.core.domain.TicketPriority
import dsupport.core.guard.Patterns
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.port.TicketGateway
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/**
 * Producer and reviewer with a limit: the agent proposes, plain code checks, and only if that passes a model reviews.
 * After [maxRevisions] rejected fixes the code opens a ticket and a human takes over.
 */
class ReviewLoop(
    private val producer: Agent,
    private val reviewer: LlmClient,
    private val reviewerPrompt: String,
    private val tickets: TicketGateway,
    private val events: EventSink,
    private val maxRevisions: Int = 2,
    private val validator: SupportAnswerValidator = SupportAnswerValidator(),
) {

    suspend fun answer(question: String): SupportAnswer {
        producer.run(question)
        var intent = "other"
        for (round in 0..maxRevisions) {
            val draft = decode(producer.finish(SupportAnswerSchema.output))
            val problems = if (draft == null) {
                listOf("the reply does not follow the ${SupportAnswerSchema.output.name} format")
            } else {
                intent = draft.intent
                validator.problems(draft, producer.evidence).ifEmpty { review(question, draft) }
            }
            events.emit(AgentEvent.ReviewRound(round + 1, problems.isEmpty(), problems))
            if (draft != null && problems.isEmpty()) return draft
            if (round == maxRevisions) break
            producer.followUp(
                "A reviewer rejected your answer. Fix these points and answer the customer again:\n" +
                    problems.joinToString("\n") { "- $it" },
            )
        }
        return escalate(question, intent)
    }

    private fun decode(json: JsonObject): SupportAnswer? = try {
        SupportAnswer.fromJson(json)
    } catch (e: SerializationException) {
        null
    }

    private suspend fun review(question: String, draft: SupportAnswer): List<String> {
        val input = buildString {
            appendLine("Customer message:")
            appendLine(question)
            appendLine()
            appendLine("Tool results:")
            appendLine(producer.evidence.joinToString("\n---\n").ifEmpty { "(none)" })
            appendLine()
            appendLine("Proposed answer:")
            append(draft.answer)
        }
        val verdict = Review.fromJson(
            reviewer.structured(LlmRequest(reviewerPrompt, listOf(Message.User(input)), emptyList()), ReviewSchema.output),
        )
        return when {
            verdict.approved -> emptyList()
            verdict.problems.isEmpty() -> listOf("the reviewer rejected the answer without a reason")
            else -> verdict.problems
        }
    }

    private suspend fun escalate(question: String, intent: String): SupportAnswer {
        val email = Patterns.email.find(question)?.value.orEmpty()
        val existing = if (email.isEmpty()) null else tickets.findOpenByEmail(email)?.ticketId
        val ticketId = existing ?: tickets.create(
            summary = "Automatic answer rejected after review. Customer message: ${Patterns.redact(question)}",
            priority = TicketPriority.MEDIUM,
            customerEmail = email,
            reason = "review_rejected",
        )
        events.emit(AgentEvent.Escalated(ticketId, "review limit reached"))
        return SupportAnswer(
            answer = "I want to give you a correct answer, so I forwarded your case to a human agent. " +
                "Your ticket number: $ticketId.",
            intent = intent,
            confidence = 0.0,
            escalated = true,
            ticketId = ticketId,
        )
    }
}
