package dsupport.core.guard

import dsupport.core.tool.Tool
import dsupport.core.tool.ToolSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardPolicyTest {
    private val policy = GuardPolicy()
    private val schema = buildJsonObject { put("type", "object") }

    private fun tool(name: String, destructive: Boolean) = mockk<Tool>().also {
        every { it.spec } returns ToolSpec(name, "desc", schema, readOnly = !destructive, destructive = destructive)
    }

    private fun guardCovering(vararg names: String) = mockk<Hook>().also { every { it.covers } returns names.toSet() }

    @Test
    fun `given a destructive tool without a guard when validating the assembly then should fail naming the tool`() {
        val failure = assertFailsWith<IllegalStateException> {
            policy.validate(listOf(tool("create_ticket", destructive = true)), listOf(guardCovering("get_order_status")))
        }

        assertTrue("create_ticket" in failure.message.orEmpty())
    }

    @Test
    fun `given a destructive tool covered by a guard when validating the assembly then should pass`() {
        policy.validate(listOf(tool("create_ticket", destructive = true)), listOf(guardCovering("create_ticket")))
    }

    @Test
    fun `given a read only tool without a guard when validating the assembly then should pass`() {
        policy.validate(listOf(tool("search_kb", destructive = false)), emptyList())
    }
}
