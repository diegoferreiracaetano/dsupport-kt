package dsupport.core.agent

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dsupport.core.tools.argsOf
import dsupport.core.tools.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubAgentToolTest {
    private fun agentAnswering(text: String) = mockk<Agent>().also { coEvery { it.run(any()) } returns text }

    @Test
    fun `given a task when running then should create a new agent run it with that task and return its answer`() = runTest {
        val specialist = mockk<Agent>().also { coEvery { it.run("check order 1042") } returns "late by 10 days" }
        val tool = SubAgentTool("orders", "Order specialist.") { specialist }

        val result = tool.run(argsOf("task" to "check order 1042"))

        assertEquals("late by 10 days", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun `given two simultaneous calls when running then should create two distinct agents`() = runTest {
        val created = mutableListOf<Agent>()
        val tool = SubAgentTool("orders", "Order specialist.") { agentAnswering("ok").also { created += it } }

        coroutineScope {
            listOf(
                async { tool.run(argsOf("task" to "order 1042")) },
                async { tool.run(argsOf("task" to "order 1046")) },
            ).awaitAll()
        }

        assertEquals(2, created.size)
        assertTrue(created[0] !== created[1])
    }

    @Test
    fun `given a specialist that throws when running then should return an error result`() = runTest {
        val failing = mockk<Agent>().also { coEvery { it.run(any()) } throws IllegalStateException("model down") }
        val tool = SubAgentTool("orders", "Order specialist.") { failing }

        val result = tool.run(argsOf("task" to "order 1042"))

        assertTrue(result.isError)
        assertTrue("orders" in result.content && "model down" in result.content)
    }

    @Test
    fun `given a call without a task when running then should answer missing_argument without creating an agent`() = runTest {
        var created = 0
        val tool = SubAgentTool("orders", "Order specialist.") { created++; agentAnswering("ok") }

        val result = tool.run(argsOf()).json()

        assertFalse(result.getValue("ok").jsonPrimitive.boolean)
        assertEquals("missing_argument:task", result.getValue("error").jsonPrimitive.content)
        assertEquals(0, created)
    }

    @Test
    fun `given the tool when reading its spec then should expose its name and description and require a task string`() {
        val spec = SubAgentTool("orders", "Order specialist.") { agentAnswering("ok") }.spec

        assertEquals("orders", spec.name)
        assertEquals("Order specialist.", spec.description)
        assertEquals(listOf("task"), spec.inputSchema.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("string", spec.inputSchema.getValue("properties").jsonObject.getValue("task").jsonObject.getValue("type").jsonPrimitive.content)
    }
}
