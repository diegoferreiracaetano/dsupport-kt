package dsupport.adapters.rag

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VectorKnowledgeBaseTest {
    private val embedder = mockk<Embedder>()
    private val chunks = listOf(
        Chunk("a#0", "a", "Alpha", "alpha text"),
        Chunk("b#0", "b", "Beta", "beta text"),
        Chunk("c#0", "c", "Gamma", "gamma text"),
    )

    private suspend fun knowledgeBase(): VectorKnowledgeBase {
        coEvery { embedder.embedDocuments(any()) } returns listOf(
            doubleArrayOf(1.0, 0.0),
            doubleArrayOf(0.0, 1.0),
            doubleArrayOf(0.6, 0.8),
        )
        return VectorKnowledgeBase.create(embedder, chunks)
    }

    @Test
    fun `given a query vector when searching then should rank the chunks by similarity`() = runTest {
        val knowledgeBase = knowledgeBase()
        coEvery { embedder.embedQuery("beta question") } returns doubleArrayOf(0.0, 1.0)

        val hits = knowledgeBase.search("beta question", k = 3)

        assertEquals(listOf("b", "c", "a"), hits.map { it.docId })
        assertEquals(listOf(1.0, 0.8, 0.0), hits.map { it.score })
    }

    @Test
    fun `given a limit when searching then should return at most that many hits`() = runTest {
        val knowledgeBase = knowledgeBase()
        coEvery { embedder.embedQuery(any()) } returns doubleArrayOf(1.0, 0.0)

        assertEquals(1, knowledgeBase.search("anything", k = 1).size)
    }

    @Test
    fun `given a chunk when searching then should return its title and text as the snippet`() = runTest {
        val knowledgeBase = knowledgeBase()
        coEvery { embedder.embedQuery(any()) } returns doubleArrayOf(1.0, 0.0)

        val hit = knowledgeBase.search("anything", k = 1).single()

        assertEquals("Alpha", hit.title)
        assertEquals("alpha text", hit.snippet)
    }

    @Test
    fun `given a score with many decimals when searching then should round it to three decimals`() = runTest {
        val knowledgeBase = knowledgeBase()
        coEvery { embedder.embedQuery(any()) } returns doubleArrayOf(0.123456, 0.0)

        val hit = knowledgeBase.search("anything", k = 1).single()

        assertEquals(0.123, hit.score)
    }
}
