package dsupport.adapters.rag

import dsupport.core.port.KnowledgeSearch
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RELEVANCE_CUT_OFF = 0.25

class KnowledgeBaseRetrievalTest {
    private suspend fun knowledgeBase(): KnowledgeSearch =
        VectorKnowledgeBase.create(TfIdfEmbedder(), KbLoader().load(Path.of("..", "data", "kb")))

    private suspend fun topDocument(question: String): String {
        val hit = knowledgeBase().search(question, k = 1).single()
        assertTrue(hit.score >= RELEVANCE_CUT_OFF, "score ${hit.score} is below the cut-off for: $question")
        return hit.docId
    }

    @Test
    fun `given a question about the return window when searching the real articles then should find the returns article`() = runTest {
        assertEquals("returns", topDocument("How many days do I have to return an item?"))
    }

    @Test
    fun `given a question about refund timing when searching the real articles then should find the refunds article`() = runTest {
        assertEquals("refunds", topDocument("How long does a refund take to arrive?"))
    }

    @Test
    fun `given a question about shipping costs when searching the real articles then should find the shipping article`() = runTest {
        assertEquals("shipping", topDocument("Is shipping free?"))
    }

    @Test
    fun `given a question about the tracking code when searching the real articles then should find the tracking article`() = runTest {
        assertEquals("order-tracking", topDocument("Where can I find my tracking code?"))
    }

    @Test
    fun `given a question about a late package when searching the real articles then should find the late delivery article`() = runTest {
        assertEquals("late-delivery", topDocument("My package is late, what should I do?"))
    }

    @Test
    fun `given a question about payment methods when searching the real articles then should find the payments article`() = runTest {
        assertEquals("payments", topDocument("Which payment methods do you accept?"))
    }

    @Test
    fun `given a question about accidental damage when searching the real articles then should find the warranty article`() = runTest {
        assertEquals("warranty", topDocument("Does the warranty cover accidental damage?"))
    }

    @Test
    fun `given a question about a forgotten password when searching the real articles then should find the account article`() = runTest {
        assertEquals("account", topDocument("I forgot my password"))
    }

    @Test
    fun `given a question about canceling a shipped order when searching the real articles then should find the cancellation article`() = runTest {
        assertEquals("cancellation", topDocument("Can I cancel my order after it shipped?"))
    }

    @Test
    fun `given a question about deleting personal data when searching the real articles then should find the privacy article`() = runTest {
        assertEquals("privacy", topDocument("Can I delete my personal data?"))
    }

    @Test
    fun `given off topic questions when searching the real articles then should score below the relevance cut-off`() = runTest {
        val questions = listOf(
            "Do you sell airline tickets to Japan?",
            "What is the weather today?",
            "Do you gift wrap?",
            "Can I talk to a human?",
        )

        questions.forEach { question ->
            val hit = knowledgeBase().search(question, k = 1).single()
            assertTrue(hit.score < RELEVANCE_CUT_OFF, "score ${hit.score} reached the cut-off for: $question")
        }
    }
}
