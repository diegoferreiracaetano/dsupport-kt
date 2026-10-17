package dsupport.adapters.rag

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class KbLoaderTest {
    private val chunker = mockk<MarkdownChunker>()
    private val loader = KbLoader(chunker)

    private fun chunkOf(docId: String) = Chunk("$docId#0", docId, docId, "text of $docId")

    @Test
    fun `given a folder with markdown files when loading then should chunk each file by its name in sorted order`() {
        val dir = createTempDirectory()
        dir.resolve("b.md").writeText("# B")
        dir.resolve("a.md").writeText("# A")
        dir.resolve("notes.txt").writeText("ignored")
        every { chunker.chunk("a", "# A") } returns listOf(chunkOf("a"))
        every { chunker.chunk("b", "# B") } returns listOf(chunkOf("b"))

        val chunks = loader.load(dir)

        assertEquals(listOf("a", "b"), chunks.map { it.docId })
        verify(exactly = 2) { chunker.chunk(any(), any()) }
    }
}
