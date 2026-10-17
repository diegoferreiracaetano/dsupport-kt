package dsupport.core.tools

import dsupport.core.tool.Tool
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalToolProviderTest {
    @Test
    fun `given a list of tools when listing them then should return exactly those tools`() = runTest {
        val tools = listOf(mockk<Tool>(), mockk<Tool>())

        val listed = LocalToolProvider(tools).tools()

        assertEquals(tools, listed)
    }
}
