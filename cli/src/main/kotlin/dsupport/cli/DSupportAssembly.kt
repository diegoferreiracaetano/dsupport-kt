package dsupport.cli

import dsupport.core.agent.Agent
import dsupport.core.agent.AgentDef
import dsupport.core.agent.AgentsConfig
import dsupport.core.agent.ESCALATION_PROMPT
import dsupport.core.agent.EventSink
import dsupport.core.agent.KNOWLEDGE_PROMPT
import dsupport.core.agent.ORCHESTRATOR_PROMPT
import dsupport.core.agent.ORDERS_PROMPT
import dsupport.core.agent.REVIEWER_PROMPT
import dsupport.core.agent.SubAgentTool
import dsupport.core.agent.ToolCatalog
import dsupport.core.guard.GuardPolicy
import dsupport.core.guard.Hook
import dsupport.core.guard.IdentityGate
import dsupport.core.guard.MentionTicket
import dsupport.core.guard.RedactResults
import dsupport.core.guard.Redaction
import dsupport.core.guard.TicketDedup
import dsupport.core.llm.LlmClient
import dsupport.core.port.TicketGateway
import dsupport.core.review.ReviewLoop
import dsupport.core.tool.Tool
import dsupport.core.tool.ToolProvider
import dsupport.core.usecase.AnswerCustomer

const val ORCHESTRATOR_NAME = "orchestrator"
private const val ORCHESTRATOR_MAX_TURNS = 8

private class Specialist(val name: String, val prompt: String, val description: String)

private val SPECIALISTS = listOf(
    Specialist(
        "knowledge",
        KNOWLEDGE_PROMPT,
        "Help-center specialist: policies and how things work (returns, refunds, shipping, payments, warranty, " +
            "cancellation, privacy, the account), including how-to questions such as where to find a tracking code.",
    ),
    Specialist(
        "orders",
        ORDERS_PROMPT,
        "Order specialist: status, delays and tracking of one specific order. " +
            "The task must include the order number and the customer email.",
    ),
    Specialist(
        "escalation",
        ESCALATION_PROMPT,
        "Opens a ticket for a human agent. The task must include the case summary and the customer email.",
    ),
)

/**
 * Builds the whole assistant from its parts: the tools each specialist may use, the guards, and a review loop.
 * What is built once is checked once; what holds a conversation (agents, guard state) is built per question.
 */
class DSupportAssembly(
    private val llm: LlmClient,
    private val toolProviders: List<ToolProvider>,
    private val agents: AgentsConfig,
    private val tickets: TicketGateway,
    private val maxRevisions: Int = 2,
) {

    suspend fun answerCustomer(): AnswerCustomer {
        check(agents.agents[ORCHESTRATOR_NAME]?.tools.isNullOrEmpty()) { "the orchestrator must not have data tools" }
        val catalog = ToolCatalog(toolProviders)
        val toolsOf = SPECIALISTS.associate { it.name to catalog.select(definitionOf(it.name).tools) }
        GuardPolicy().validate(toolsOf.values.flatten(), newGuards())
        return AnswerCustomer { sink -> loopFor(sink, toolsOf) }
    }

    private fun definitionOf(name: String): AgentDef =
        agents.agents[name] ?: throw IllegalArgumentException("missing agent definition: $name")

    private fun newGuards(): List<Hook> =
        listOf(Redaction(), IdentityGate(), RedactResults(), MentionTicket(), TicketDedup(tickets))

    private fun loopFor(sink: EventSink, toolsOf: Map<String, List<Tool>>): ReviewLoop {
        val guards = newGuards()
        val specialists = SPECIALISTS.map { specialist ->
            SubAgentTool(specialist.name, specialist.description) {
                Agent(
                    name = specialist.name,
                    llm = llm,
                    system = specialist.prompt,
                    tools = toolsOf.getValue(specialist.name),
                    hooks = guards,
                    events = sink,
                    maxTurns = definitionOf(specialist.name).maxTurns,
                    depth = 1,
                )
            }
        }
        val orchestrator = Agent(
            name = ORCHESTRATOR_NAME,
            llm = llm,
            system = ORCHESTRATOR_PROMPT,
            tools = specialists,
            hooks = guards,
            events = sink,
            maxTurns = ORCHESTRATOR_MAX_TURNS,
        )
        return ReviewLoop(orchestrator, llm, REVIEWER_PROMPT, tickets, sink, maxRevisions)
    }
}
