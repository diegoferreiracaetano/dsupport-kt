package dsupport.core.tools

import dsupport.core.port.Hit
import dsupport.core.port.KnowledgeSearch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchKbToolTest {
    private val kb = mockk<KnowledgeSearch>()
    private val tool = SearchKbTool(kb, minScore = 0.25)

    private fun hit(docId: String, score: Double) = Hit(docId, "Title of $docId", "Snippet of $docId", score)

    @Test
    fun `given relevant hits when searching then should return them with title snippet and score`() = runTest {
        coEvery { kb.search("return window", 3) } returns listOf(hit("returns", 0.8), hit("refunds", 0.4))

        val result = tool.run(argsOf("query" to "return window")).json()

        assertTrue(result.getValue("ok").jsonPrimitive.boolean)
        val first = result.getValue("results").jsonArray.first().jsonObject
        assertEquals("returns", first.getValue("doc_id").jsonPrimitive.content)
        assertEquals("Title of returns", first.getValue("title").jsonPrimitive.content)
        assertEquals("Snippet of returns", first.getValue("snippet").jsonPrimitive.content)
        assertEquals(0.8, first.getValue("score").jsonPrimitive.double)
    }

    @Test
    fun `given hits below the cut-off when searching then should drop them`() = runTest {
        coEvery { kb.search(any(), any()) } returns listOf(hit("returns", 0.8), hit("privacy", 0.1))

        val result = tool.run(argsOf("query" to "return")).json()

        assertEquals(1, result.getValue("results").jsonArray.size)
    }

    @Test
    fun `given only irrelevant hits when searching then should answer nothing_relevant_found`() = runTest {
        coEvery { kb.search(any(), any()) } returns listOf(hit("privacy", 0.1))

        val result = tool.run(argsOf("query" to "airline tickets")).json()

        assertFalse(result.getValue("ok").jsonPrimitive.boolean)
        assertEquals("nothing_relevant_found", result.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `given a missing query when searching then should answer missing_argument without searching`() = runTest {
        val result = tool.run(argsOf()).json()

        assertEquals("missing_argument:query", result.getValue("error").jsonPrimitive.content)
        coVerify(exactly = 0) { kb.search(any(), any()) }
    }

    @Test
    fun `given the tool when reading its spec then should require a query and stay read only`() {
        assertEquals("search_kb", tool.spec.name)
        assertEquals(
            listOf("query"),
            tool.spec.inputSchema.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(tool.spec.readOnly)
    }
}
