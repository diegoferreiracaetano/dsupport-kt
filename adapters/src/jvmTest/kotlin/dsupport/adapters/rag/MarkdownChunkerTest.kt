package dsupport.adapters.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownChunkerTest {
    private val chunker = MarkdownChunker()

    private val article = """
        # Returns Policy

        Intro text.

        ## Return window
        30 days.

        ## Costs
        Free for defects.
    """.trimIndent()

    @Test
    fun `given an article with sections when chunking then should produce the intro and one chunk per section`() {
        val chunks = chunker.chunk("returns", article)

        assertEquals(listOf("returns#0", "returns#1", "returns#2"), chunks.map { it.id })
        assertTrue(chunks.all { it.docId == "returns" && it.title == "Returns Policy" })
    }

    @Test
    fun `given a section when chunking then should prefix it with the article title`() {
        val chunks = chunker.chunk("returns", article)

        assertTrue(chunks[1].text.startsWith("Returns Policy — Return window"))
        assertTrue("30 days." in chunks[1].text)
    }

    @Test
    fun `given an article without sections when chunking then should produce a single chunk`() {
        val chunks = chunker.chunk("faq", "# FAQ\nJust one paragraph.")

        assertEquals(1, chunks.size)
    }

    @Test
    fun `given an article without a title when chunking then should use the document id as the title`() {
        val chunks = chunker.chunk("faq", "Plain text.\n\n## Part\nBody.")

        assertTrue(chunks.all { it.title == "faq" })
    }

    @Test
    fun `given an intro with only the title when chunking then should skip the intro chunk`() {
        val chunks = chunker.chunk("a", "# A\n\n## Full\nContent.")

        assertEquals(listOf("a#1"), chunks.map { it.id })
    }

    @Test
    fun `given an empty section when chunking then should skip it`() {
        val chunks = chunker.chunk("a", "# A\nIntro.\n\n## Empty\n\n## Full\nContent.")

        assertEquals(listOf("a#0", "a#2"), chunks.map { it.id })
    }
}
