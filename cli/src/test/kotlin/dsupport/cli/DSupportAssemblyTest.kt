package dsupport.cli

import dsupport.contract.AgentEvent
import dsupport.contract.GuardAction
import dsupport.core.agent.AgentDef
import dsupport.core.agent.AgentsConfig
import dsupport.core.agent.ORCHESTRATOR_PROMPT
import dsupport.core.agent.ORDERS_PROMPT
import dsupport.core.llm.LlmClient
import dsupport.core.llm.LlmReply
import dsupport.core.llm.LlmRequest
import dsupport.core.llm.StructuredReply
import dsupport.core.llm.ToolCall
import dsupport.core.llm.Usage
import dsupport.core.port.TicketGateway
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolResult
import dsupport.core.tool.ToolSpec
import dsupport.core.tools.LocalToolProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DSupportAssemblyTest {
    private val usage = Usage(10, 5)
    private val schema = buildJsonObject { put("type", "object") }
    private val llm = mockk<LlmClient>()
    private val tickets = mockk<TicketGateway>()
    private val requests = mutableListOf<LlmRequest>()

    private fun tool(name: String, destructive: Boolean = false) = mockk<Tool>().also {
        every { it.spec } returns ToolSpec(name, "Tool $name.", schema, readOnly = !destructive, destructive = destructive)
        coEvery { it.run(any()) } returns ToolResult("""{"ok":true}""")
    }

    private val searchKb = tool("search_kb")
    private val getOrderStatus = tool("get_order_status")
    private val trackShipment = tool("track_shipment")
    private val createTicket = tool("create_ticket", destructive = true)

    private val standard = AgentsConfig(
        mapOf(
            "knowledge" to AgentDef(listOf("search_kb")),
            "orders" to AgentDef(listOf("get_order_status", "track_shipment")),
            "escalation" to AgentDef(listOf("create_ticket")),
        ),
    )

    private fun assembly(
        config: AgentsConfig = standard,
        extra: List<Tool> = emptyList(),
    ) = DSupportAssembly(
        llm,
        listOf(LocalToolProvider(listOf(searchKb, getOrderStatus, trackShipment, createTicket) + extra)),
        config,
        tickets,
    )

    private fun json(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    private fun stubStructured(text: String) {
        val answer = """{"answer":"$text","intent":"order","sources":[],"confidence":0.9,"escalated":false,"ticket_id":null}"""
        coEvery { llm.structured(any(), match { it.name == "answer_customer" }) } returns StructuredReply(json(answer), usage)
        coEvery { llm.structured(any(), match { it.name == "review_verdict" }) } returns
            StructuredReply(json("""{"approved":true,"problems":[]}"""), usage)
    }

    @Test
    fun `given the standard configuration when answering then should give the orchestrator only the specialists as tools`() = runTest {
        coEvery { llm.next(capture(requests)) } returns LlmReply.Text("Hello, how can I help?", usage)
        stubStructured("Hello, how can I help?")

        val events = assembly().answerCustomer()("Hi").toList()

        assertEquals(listOf("knowledge", "orders", "escalation"), requests.first().tools.map { it.name })
        assertEquals(ORCHESTRATOR_PROMPT, requests.first().system)
        assertIs<AgentEvent.Completed>(events.last())
    }

    @Test
    fun `given a delegation to the orders specialist when answering then should give it only its configured tools and prompt`() = runTest {
        val delegation = ToolCall("o1", "orders", buildJsonObject { put("task", "check order 1042") })
        coEvery { llm.next(capture(requests)) } returnsMany listOf(
            LlmReply.ToolCalls(listOf(delegation), usage),
            LlmReply.Text("order 1042 is in transit", usage),
            LlmReply.Text("Your order is in transit.", usage),
        )
        stubStructured("Your order is in transit.")

        assembly().answerCustomer()("Where is order 1042?").toList()

        assertEquals(listOf("get_order_status", "track_shipment"), requests[1].tools.map { it.name })
        assertEquals(ORDERS_PROMPT, requests[1].system)
    }

    @Test
    fun `given the orders specialist calling an order tool without an email when answering then should block the call with the identity guard`() = runTest {
        val delegation = ToolCall("o1", "orders", buildJsonObject { put("task", "check order 1042") })
        val lookup = ToolCall("s1", "get_order_status", buildJsonObject { put("order_id", "1042") })
        coEvery { llm.next(any()) } returnsMany listOf(
            LlmReply.ToolCalls(listOf(delegation), usage),
            LlmReply.ToolCalls(listOf(lookup), usage),
            LlmReply.Text("I need the customer email.", usage),
            LlmReply.Text("Please tell me your email.", usage),
        )
        stubStructured("Please tell me your email.")

        val events = assembly().answerCustomer()("Where is order 1042?").toList()

        coVerify(exactly = 0) { getOrderStatus.run(any()) }
        assertTrue(events.any { it is AgentEvent.GuardActed && it.guard == "IdentityGate" && it.action == GuardAction.DENY })
    }

    @Test
    fun `given a configuration that gives the orchestrator a data tool when assembling then should fail`() = runTest {
        val config = AgentsConfig(standard.agents + ("orchestrator" to AgentDef(listOf("get_order_status"))))

        val failure = assertFailsWith<IllegalStateException> { assembly(config).answerCustomer() }

        assertTrue("orchestrator" in failure.message.orEmpty())
    }

    @Test
    fun `given a destructive tool that no guard covers when assembling then should fail naming the tool`() = runTest {
        val unguarded = tool("delete_everything", destructive = true)
        val config = AgentsConfig(standard.agents + ("orders" to AgentDef(listOf("get_order_status", "delete_everything"))))

        val failure = assertFailsWith<IllegalStateException> { assembly(config, listOf(unguarded)).answerCustomer() }

        assertTrue("delete_everything" in failure.message.orEmpty())
    }

    @Test
    fun `given a configuration that names an unknown tool when assembling then should fail`() = runTest {
        val config = AgentsConfig(standard.agents + ("knowledge" to AgentDef(listOf("search_the_moon"))))

        val failure = assertFailsWith<IllegalArgumentException> { assembly(config).answerCustomer() }

        assertTrue("search_the_moon" in failure.message.orEmpty())
    }

    @Test
    fun `given a configuration without a specialist when assembling then should fail naming it`() = runTest {
        val config = AgentsConfig(standard.agents - "escalation")

        val failure = assertFailsWith<IllegalArgumentException> { assembly(config).answerCustomer() }

        assertTrue("escalation" in failure.message.orEmpty())
    }
}
