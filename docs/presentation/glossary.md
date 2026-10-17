# Presentation glossary

[English](glossary.md) | [Português](glossary.pt-BR.md)

**Rule:** every stage title uses the **real name of the concept**, with the acronym spelled out the first time, followed by a plain-language subtitle. The goal is that the audience leaves knowing the words the industry uses (RAG, MCP, guardrails, evals), not only an analogy. The analogy supports the name, it never replaces it.

Format of a title: `Stage 4 — RAG (Retrieval-Augmented Generation): answering with a consultation`.

| Stage | Concept (real name) | Spelled out | In plain language | Analogy |
|---|---|---|---|---|
| 1 | **Agent loop** (agentic loop) | the loop that makes a model an *agent* | The model decides, your code acts, the result goes back, until there is an answer | A manager who only gives orders and an assistant who does the work |
| 2 | **LLM client** | LLM = Large Language Model | One port to talk to any language model, with one adapter per provider | A socket and a plug: swap the brain, keep the system |
| 3 | **Tools** (tool use, function calling) | | Functions the model can ask the code to run | Giving the assistant hands: look up an order, open a ticket |
| 4 | **RAG** | Retrieval-Augmented Generation | Search trusted documents first, answer from what was found, cite the source | An exam where you may consult the manual |
| 4 | **Embeddings**, **chunking**, **TF-IDF** | | Turn text into numbers to find similar passages; split articles into passages; a simple word-weighting scheme | A map where similar meanings sit close together |
| 5 | **MCP** | Model Context Protocol | An open standard for connecting tools to agents, over separate processes | USB-C: any tool that speaks it plugs into any agent |
| 6 | **Multi-agent orchestration** (orchestrator and sub-agents) | | A coordinator agent delegates to specialist agents, in parallel when possible | A reception desk sending each question to the right expert |
| 7 | **Guardrails** (guards, hooks) | | Deterministic rules enforced by code around the model: block, correct, rewrite | Safety rails the model can neither see nor skip |
| 8.1 | **Structured output** and **validation** | | The model fills a fixed JSON schema; code checks each field | An answer as a form instead of free text |
| 8.2 | **Producer–reviewer loop** (LLM review, bounded retries, escalation) | | One step proposes, another reviews, a limit stops endless loops, a human takes over | An editor reading before it goes out |
| 9.1 | **Observability** (events, tracing, cost per agent) | | Every step, tool call and token is recorded | A flight recorder |
| 9.2 | **Evals** (evaluation, golden dataset) | | A fixed set of questions with known answers, scored by metrics: routing accuracy, groundedness, escalation precision and recall | An exam with an answer key |
| 11 | **API server** | | The assistant behind a web address, streaming its events | Opening the doors |
| 12 | **Client app** | | The chat screen with sources, ticket badge and live trace | The face of the assistant |

More terms: **escalation** (handing the case to a human), **groundedness** (is each claim backed by a source), **hallucination** (an invented fact), **prompt** (the instructions given to the model), **token** (the unit a model reads and bills by).
