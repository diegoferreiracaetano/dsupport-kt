package dsupport.core.guard

import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class RedactResultsTest {
    private val guard = RedactResults()
    private val schema = buildJsonObject { put("type", "object") }
    private val spec = ToolSpec("orders", "desc", schema)
    private val noArgs = JsonObject(emptyMap())

    @Test
    fun `given a result with a national id and a card number when running a tool then should replace them`() = runTest {
        val result = ToolResult("Customer confirmed id 123.456.789-00 and card 4111 1111 1111 1111.")

        val redacted = guard.afterTool("orders", spec, noArgs, result)

        assertEquals("Customer confirmed id [ID REMOVED] and card [CARD REMOVED].", redacted.content)
    }

    @Test
    fun `given an error result when redacting then should keep it marked as an error`() = runTest {
        val result = ToolResult("failed for 123.456.789-00", isError = true)

        val redacted = guard.afterTool("orders", spec, noArgs, result)

        assertEquals(ToolResult("failed for [ID REMOVED]", isError = true), redacted)
    }

    @Test
    fun `given a clean result when redacting then should return it unchanged`() = runTest {
        val result = ToolResult("""{"ok":true,"status":"delivered"}""")

        assertEquals(result, guard.afterTool("orders", spec, noArgs, result))
    }
}
