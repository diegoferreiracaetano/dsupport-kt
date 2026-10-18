# Building a multi-agent support assistant by hand in Kotlin: what I learned

[English](article.md) | [Português](article.pt-BR.md)

> Draft for review. Real excerpts come from the tags of the repository (`git checkout etapa-N`).

## The problem

Customers ask about orders, returns and policies. Most questions have an answer in a help center or in an order system, and a few need a person. I wanted an assistant that answers the first group, hands the second to a human, and never invents a fact. I also wanted to understand every moving part, so I built it without an agent framework.

## The architecture in one picture

`contract` ← `core` ← `adapters` ← applications (`cli`, `eval`, `server`, MCP servers, `app`). The core owns the ports (`LlmClient`, `Tool`, `ToolProvider`) and the adapters implement them. Because `contract` and `core` are Kotlin Multiplatform, a client on another platform can reuse them.

## What I built, stage by stage

1. **Agent loop.** The model decides, the code acts. The loop is bounded by a maximum number of turns, and an unknown tool returns an error instead of crashing.
2. **LLM client.** One port, one Anthropic adapter over plain HTTP. The mapping between our messages and the provider's JSON is isolated, so a second provider is a new adapter.
3. **Tools.** Order status, shipment tracking and ticket creation. A tool reports failure as a result the model can read.
4. **RAG.** Help-center articles are split into chunks, scored with TF-IDF, and returned only above a relevance cut-off. The answer cites its sources.
5. **MCP.** The order and ticket tools moved into separate servers using the official Kotlin SDK. The agent still sees a `Tool`. A dead server becomes an error result, never a hang.
6. **Multi-agent orchestration.** An orchestrator delegates to knowledge, orders and escalation specialists. Calls in the same turn run in parallel and keep their order.
7. **Guardrails.** Code around the model: it checks the order number and email before a lookup, redacts personal data, and collapses duplicate tickets. The model cannot see or skip these rules.
8. **Structured output and a reviewer.** The final answer is a JSON form validated by code (an invented ticket id is rejected). A second model reviews it, with at most two revisions, and then code escalates to a human.
9. **Observability and evals.** Every step is an event with a run id and a parent run id, which gives a trace tree and a cost per agent. A golden dataset of 15 cases, run three times each, scores routing, groundedness, facts, sources, leaks and escalation.
10. **A server and an app.** A Ktor server streams the events as Server-Sent Events, and a Compose Desktop app draws the conversation and the live trace.

## The numbers

| Metric | First run | After fixes |
|---|---|---|
| Expected facts present | 93% | 100% |
| Expected sources cited | 86% | 100% |
| Escalation precision | 80% | 100% |
| Review fallback | 9% | 0% |
| Average latency | 11.0 s | 9.8 s |
| Tokens per case | 9,510 | 9,053 |

The evals found three real problems: a confidence rule that treated asking the customer for a detail as uncertainty, a reviewer that was too strict, and how-to questions routed to the wrong specialist. I fixed each, re-ran, and kept the log in `docs/evals/improvement-log.md`.

## Lessons learned

- **Put rules in code, not in the prompt.** Guards and validators are deterministic and testable.
- **Measure before and after.** Without the evals I would have trusted a demo.
- **Keep the model on a short leash.** Bounded turns, bounded revisions, and a human as the last step.
- **Ports make cost visible.** With doubles at the ports, the whole test suite runs offline.
- **Watch the cost.** Real runs cost cents, but repeated evals add up, so announce the cost first.

## Run it

The repository README explains how to run the tests, the terminal assistant, the server and the app.
