package dsupport.core.agent

import dsupport.core.tool.Tool
import dsupport.core.tool.ToolProvider
import dsupport.core.tool.ToolSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToolCatalogTest {
    private val schema = buildJsonObject { put("type", "object") }

    private fun tool(name: String) = mockk<Tool>().also { every { it.spec } returns ToolSpec(name, "d", schema) }

    private fun provider(vararg tools: Tool) = mockk<ToolProvider>().also { coEvery { it.tools() } returns tools.toList() }

    @Test
    fun `given a provider when selecting names then should return those tools in the order of the names`() = runTest {
        val catalog = ToolCatalog(listOf(provider(tool("a"), tool("b"), tool("c"))))

        val selected = catalog.select(listOf("c", "a"))

        assertEquals(listOf("c", "a"), selected.map { it.spec.name })
    }

    @Test
    fun `given two providers when selecting names from both then should return the tools of each`() = runTest {
        val catalog = ToolCatalog(listOf(provider(tool("a")), provider(tool("b"))))

        assertEquals(listOf("a", "b"), catalog.select(listOf("a", "b")).map { it.spec.name })
    }

    @Test
    fun `given an unknown name when selecting then should throw IllegalArgumentException naming it`() = runTest {
        val catalog = ToolCatalog(listOf(provider(tool("a"))))

        val failure = assertFailsWith<IllegalArgumentException> { catalog.select(listOf("a", "ghost")) }

        assertTrue("ghost" in failure.message.orEmpty())
    }

    @Test
    fun `given two providers exposing the same tool name when selecting then should throw IllegalStateException`() = runTest {
        val catalog = ToolCatalog(listOf(provider(tool("a")), provider(tool("a"))))

        val failure = assertFailsWith<IllegalStateException> { catalog.select(listOf("a")) }

        assertTrue("a" in failure.message.orEmpty())
    }

    @Test
    fun `given several selections when selecting then should consult each provider only once`() = runTest {
        val first = provider(tool("a"))
        val catalog = ToolCatalog(listOf(first))

        catalog.select(listOf("a"))
        catalog.select(listOf("a"))

        coVerify(exactly = 1) { first.tools() }
    }
}
