package dsupport.core.agent

import kotlin.test.Test
import kotlin.test.assertTrue

class PromptsTest {
    private val orchestrator = ORCHESTRATOR_PROMPT.lowercase()

    @Test
    fun `given the orchestrator prompt when reading it then should send how to and where to find questions to the knowledge specialist`() {
        assertTrue("where do i find my tracking code" in orchestrator)
        assertTrue("knowledge" in orchestrator.substringAfter("where do i find my tracking code"))
    }

    @Test
    fun `given the orchestrator prompt when reading it then should reserve the orders specialist for a specific order`() {
        assertTrue("specific order" in orchestrator)
    }

    @Test
    fun `given the orchestrator prompt when reading it then should treat asking the customer for a detail as a complete answer`() {
        assertTrue("ask" in orchestrator && "complete answer" in orchestrator)
    }

    @Test
    fun `given the knowledge prompt when reading it then should keep the exact NO_ANSWER signal`() {
        assertTrue("NO_ANSWER" in KNOWLEDGE_PROMPT)
    }

    @Test
    fun `given the reviewer prompt when reading it then should accept asking the customer for a missing detail`() {
        assertTrue("asking the customer" in REVIEWER_PROMPT.lowercase())
    }

    @Test
    fun `given the reviewer prompt when reading it then should not reject for style or for offering a human agent`() {
        val reviewer = REVIEWER_PROMPT.lowercase()

        assertTrue("do not reject" in reviewer && "human agent" in reviewer)
    }

    @Test
    fun `given the reviewer prompt when reading it then should only judge claims against the results`() {
        assertTrue("claim" in REVIEWER_PROMPT.lowercase())
    }
}
