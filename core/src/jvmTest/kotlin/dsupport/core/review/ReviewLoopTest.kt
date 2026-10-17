package dsupport.core.review

import dsupport.contract.AgentEvent
import dsupport.contract.SupportAnswer
import dsupport.core.agent.Agent
import dsupport.core.agent.EventSink
import dsupport.core.domain.Ticket
import dsupport.core.domain.TicketPriority
import dsupport.core.domain.TicketStatus
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.Message
import dsupport.core.llm.StructuredReply
import dsupport.core.llm.Usage
import dsupport.core.port.TicketGateway
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewLoopTest {
    private val producer = mockk<Agent>()
    private val reviewer = mockk<LlmClient>()
    private val tickets = mockk<TicketGateway>()
    private val events = mutableListOf<AgentEvent>()
    private val reviewRequests = mutableListOf<LlmRequest>()
    private val followUps = mutableListOf<String>()
    private val evidence = listOf("""{"ok":true,"ticket_id":"TCK-1001","late":true,"days_late":10}""")
    private val question = "My order 1042 has not arrived. I am ana@example.com"

    private fun loop(maxRevisions: Int = 2) =
        ReviewLoop(producer, reviewer, "You review support answers.", tickets, EventSink { events += it }, maxRevisions)

    private fun json(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    private fun draft(text: String, ticket: String? = "TCK-1001", intent: String = "order", confidence: Double = 0.9) =
        json(
            """{"answer":"$text","intent":"$intent","sources":[],"confidence":$confidence,"escalated":${ticket != null},""" +
                """"ticket_id":${ticket?.let { "\"$it\"" } ?: "null"}}""",
        )

    private fun verdict(approved: Boolean, vararg problems: String) =
        json("""{"approved":$approved,"problems":[${problems.joinToString(",") { "\"$it\"" }}]}""")

    private fun producerDrafts(vararg drafts: JsonObject) {
        coEvery { producer.run(any()) } returns "first proposal"
        every { producer.evidence } returns evidence
        coEvery { producer.finish(any()) } returnsMany drafts.toList()
        coEvery { producer.followUp(capture(followUps)) } returns "revised proposal"
    }

    private fun reviewerSays(vararg verdicts: JsonObject) {
        coEvery { reviewer.structured(capture(reviewRequests), any()) } returnsMany verdicts.map { StructuredReply(it, Usage(20, 10)) }
    }

    private val goodText = "Your order is 10 days late, ticket TCK-1001 was opened."

    @Test
    fun `given a draft that passes the code checks and the reviewer approves when answering then should return it after one round`() = runTest {
        producerDrafts(draft(goodText))
        reviewerSays(verdict(true))

        val answer = loop().answer(question)

        assertEquals(goodText, answer.answer)
        assertTrue(followUps.isEmpty())
        assertEquals(listOf(AgentEvent.ReviewRound(1, true, emptyList())), events.filterIsInstance<AgentEvent.ReviewRound>())
    }

    @Test
    fun `given a draft with an invented ticket when answering then should skip the reviewer and ask the producer to fix it`() = runTest {
        producerDrafts(draft("Ticket TCK-9999 was opened.", ticket = "TCK-9999"), draft(goodText))
        reviewerSays(verdict(true))

        val answer = loop().answer(question)

        assertEquals(goodText, answer.answer)
        assertEquals(1, reviewRequests.size)
        assertTrue("TCK-9999" in followUps.single())
    }

    @Test
    fun `given a reviewer that rejects once and then approves when answering then should send the problems back and return the second draft`() = runTest {
        producerDrafts(draft("Ticket TCK-1001 was opened."), draft(goodText))
        reviewerSays(verdict(false, "does not say how many days late"), verdict(true))

        val answer = loop().answer(question)

        assertEquals(goodText, answer.answer)
        assertTrue("does not say how many days late" in followUps.single())
        assertEquals(listOf(false, true), events.filterIsInstance<AgentEvent.ReviewRound>().map { it.approved })
    }

    @Test
    fun `given a reviewer that never approves when answering then should stop after the revision limit and escalate with a new ticket`() = runTest {
        producerDrafts(draft(goodText, intent = "complaint"), draft(goodText), draft(goodText))
        reviewerSays(verdict(false, "tone"), verdict(false, "tone"), verdict(false, "tone"))
        coEvery { tickets.findOpenByEmail("ana@example.com") } returns null
        coEvery { tickets.create(any(), TicketPriority.MEDIUM, "ana@example.com", "review_rejected") } returns "TCK-1002"

        val answer = loop(maxRevisions = 2).answer(question)

        assertTrue(answer.escalated)
        assertEquals("TCK-1002", answer.ticketId)
        assertEquals(0.0, answer.confidence)
        assertTrue("TCK-1002" in answer.answer)
        coVerify(exactly = 3) { reviewer.structured(any(), any()) }
        assertEquals(2, followUps.size)
        assertTrue(events.contains(AgentEvent.Escalated("TCK-1002", "review limit reached")))
    }

    @Test
    fun `given an open ticket of the customer when escalating then should reuse it instead of creating another`() = runTest {
        producerDrafts(draft(goodText), draft(goodText), draft(goodText))
        reviewerSays(verdict(false, "tone"), verdict(false, "tone"), verdict(false, "tone"))
        coEvery { tickets.findOpenByEmail("ana@example.com") } returns Ticket(
            "TCK-1001", "2026-10-15T10:00:00Z", TicketStatus.OPEN, "late", TicketPriority.HIGH, "ana@example.com", "delay",
        )

        val answer = loop().answer(question)

        assertEquals("TCK-1001", answer.ticketId)
        coVerify(exactly = 0) { tickets.create(any(), any(), any(), any()) }
    }

    @Test
    fun `given a question with a national id when escalating then should redact it in the ticket summary`() = runTest {
        producerDrafts(draft(goodText), draft(goodText), draft(goodText))
        reviewerSays(verdict(false, "tone"), verdict(false, "tone"), verdict(false, "tone"))
        val summaries = mutableListOf<String>()
        coEvery { tickets.findOpenByEmail(any()) } returns null
        coEvery { tickets.create(capture(summaries), any(), any(), any()) } returns "TCK-1002"

        loop().answer("Order 1042 is late, my id is 123.456.789-00, ana@example.com")

        assertTrue("123.456" !in summaries.single())
        assertTrue("[ID REMOVED]" in summaries.single())
    }

    @Test
    fun `given a question without an email when escalating then should create the ticket with an empty email`() = runTest {
        producerDrafts(draft(goodText), draft(goodText), draft(goodText))
        reviewerSays(verdict(false, "tone"), verdict(false, "tone"), verdict(false, "tone"))
        coEvery { tickets.create(any(), any(), "", any()) } returns "TCK-1003"

        val answer = loop().answer("Where is my order 1042?")

        assertEquals("TCK-1003", answer.ticketId)
        coVerify(exactly = 0) { tickets.findOpenByEmail(any()) }
    }

    @Test
    fun `given a reviewer that rejects without a reason when answering then should still ask the producer for a fix`() = runTest {
        producerDrafts(draft(goodText), draft(goodText))
        reviewerSays(verdict(false), verdict(true))

        loop().answer(question)

        assertTrue("without a reason" in followUps.single())
    }

    @Test
    fun `given a reply that does not follow the format when answering then should ask the producer to fix it`() = runTest {
        val malformed = json("""{"answer":"Hi","confidence":"very high"}""")
        producerDrafts(malformed, draft(goodText))
        reviewerSays(verdict(true))

        val answer = loop().answer(question)

        assertEquals(goodText, answer.answer)
        assertTrue("format" in followUps.single())
        assertEquals(1, reviewRequests.size)
    }

    @Test
    fun `given a draft to review when calling the reviewer then should send the customer message the tool results and the proposed answer`() = runTest {
        producerDrafts(draft(goodText))
        reviewerSays(verdict(true))

        loop().answer(question)

        val request = reviewRequests.single()
        assertEquals("You review support answers.", request.system)
        val text = (request.messages.single() as Message.User).text
        assertTrue(question in text)
        assertTrue("days_late" in text)
        assertTrue(goodText in text)
        assertTrue(request.tools.isEmpty())
    }

    @Test
    fun `given a draft to review when calling the reviewer then should emit a ModelCalled event for the reviewer`() = runTest {
        producerDrafts(draft(goodText))
        reviewerSays(verdict(true))

        loop().answer(question)

        val call = events.filterIsInstance<AgentEvent.ModelCalled>().single { it.agent == "reviewer" }
        assertEquals(20, call.inputTokens)
        assertEquals(10, call.outputTokens)
    }

    @Test
    fun `given the revision limit of zero when the first draft is rejected then should escalate without asking for a fix`() = runTest {
        producerDrafts(draft(goodText))
        reviewerSays(verdict(false, "tone"))
        coEvery { tickets.findOpenByEmail(any()) } returns null
        coEvery { tickets.create(any(), any(), any(), any()) } returns "TCK-1002"

        val answer = loop(maxRevisions = 0).answer(question)

        assertTrue(answer.escalated)
        assertTrue(followUps.isEmpty())
    }
}
