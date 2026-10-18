# DSupport — Checklist

[English](CHECKLIST.md) | [Português](CHECKLIST.pt-BR.md)

> Both files move together: every change applies to both languages.

A new project, separate from the `DsupportKotlin` study. Kotlin Multiplatform, Clean Architecture, MCP with the official SDK, generic LLM.

- Spec: `docs/superpowers/specs/2026-10-17-dsupport-nucleo-design.md`
- Phase 1 plan: `docs/superpowers/plans/2026-10-17-dsupport-nucleo.md`

**How to use:** one item at a time, in order. Claude explains first, Diego confirms after testing. Each stage closes with one commit and the tag `etapa-N`.
**Tests:** one test class per production class, MockK, names `given … when … then should …`, in `jvmTest`.
**Code:** no comments (KDoc only when it is documentation) and everything in English: names, messages, KDoc and test names.
**Author:** **[M]** Claude does it · **[D]** Diego writes it, with a guide written beforehand in `docs/stages/`.

---

## Before starting

- [ ] Review the spec and the plan (this checklist, the spec and the plan agree with each other)
- [ ] Pick the execution method (native or subagent-driven; recommended: native)
- [ ] `ANTHROPIC_API_KEY` available for stages 6 and 9.2 (Diego exports it; Claude never types keys)
- [ ] `VOYAGE_API_KEY` only if semantic embeddings are wanted (optional; TF-IDF works without a key)

## Phase 1 — Core

### E0 — Skeleton [M] · tag `etapa-0`
- [x] Versions resolved: Kotlin 2.4.21, Gradle 9.8.1, coroutines 1.11.0, serialization 1.11.0, datetime 0.8.0, Ktor 3.6.0, MCP SDK 0.15.0
- [x] Modules `contract`, `core`, `testing`, `adapters`, `mcp-orders`, `mcp-tickets`, `cli`, `eval`
- [x] JDK 21 pinned in the project (the system's JDK 26 does not get in the way)
- [x] `./gradlew test` green (the root aggregator runs `jvmTest` of the KMP modules) + Wasm compilation of `contract` and `core` (validated with throwaway code; it only counts for real from E1)
- [x] CI on GitHub Actions, `.gitignore`, `.env.example`, initial README
- [x] `git init` and first commit

### E1 — Vocabulary and agent loop [M] · tag `etapa-1`
- [x] `AgentEvent` in `contract`
- [x] Ports `LlmClient`, `Tool`, `ToolProvider`, `EventSink`
- [x] `Agent` with a loop, a turn limit and unknown tools handled
- [x] `LlmClientContract` in the `testing` module; `Agent` tests with MockK

### E2 — LLM client [D] · tag `etapa-2`
- [x] Guide `docs/stages/2-llm-client.md`
- [x] `AnthropicLlmClient` (mapping both ways, `tool_use`/`tool_result`, structured output)
- [x] API errors: 2 retries and then a clear error
- [x] Passes the `LlmClientContract` suite
- [x] Client ADR (official SDK or plain HTTP)

### E3 — Real tools [M] · tag `etapa-3`
- [x] `Order`/`Ticket` domain and `OrderGateway`/`TicketGateway` ports
- [x] `get_order_status` (with computed delay), `track_shipment`, `create_ticket`
- [x] JSON repositories in `jvmMain`; brand-free data
- [x] `LocalToolProvider`

### E4 — RAG [M] · tag `etapa-4`
- [x] Section chunker, accent folding without `java.text`
- [x] `TfIdfEmbedder` and `VoyageEmbedder`
- [x] `VectorKnowledgeBase` and `SearchKbTool` with a relevance cut-off
- [x] Help-center articles in `data/kb/`

### E5 — MCP with the official SDK [D] · tag `etapa-5`
- [x] Guide `docs/stages/5-mcp.md` (version and transport confirmed in the SDK sources)
- [x] `McpToolServer` and the `mcp-orders` and `mcp-tickets` servers
- [x] `McpConnector` (stdio) and `McpToolProvider` reading `config/mcp-servers.json`, with a per-call timeout
- [x] A server that crashes or fails to start returns an error without taking the agent down
- [x] Proof of the criterion: add the tickets server by editing the configuration only

### E6 — Multi-agent and parallelism [D, done by Claude at Diego's request] · tag `etapa-6`
- [x] Guide `docs/stages/6-multi-agents.md`
- [x] `subAgentTool` (a fresh agent per call), `ToolCatalog`, `config/agents.json` (per-agent allow-list)
- [x] Turns with several calls run in parallel, order preserved, failures isolated
- [x] Neutral prompts in English; orchestrator without data tools
- [x] `DSupportAssembly`: guards in order (`TicketDedup` last) and `GuardPolicy` at assembly
- [x] Two parallel `create_ticket` calls produce one ticket (end to end, including real parallelism)
- [x] `REFERENCE_DATE` supported (in the `cli` and in `mcp-orders`)
- [x] First end-to-end run against the API (`:cli:run`)

### E7 — Guards [M] · tag `etapa-7`
- [x] `IdentityGate`, `Redaction`, `RedactResults`, `TicketDedup`, `MentionTicket` (wired in at assembly in E6)
- [x] National id, card and email patterns in a single place (`Patterns`)
- [x] `GuardPolicy`: a destructive tool without a guard is rejected at assembly (called at assembly in E6)
- [x] Two parallel `create_ticket` calls produce a single ticket (guard level; end to end in E6)

### E8.1 — Typed output [M] · tag `etapa-8.1`
- [x] Complete `SupportAnswer` in `contract`
- [x] Validation in code (invented ticket, confidence, sources, intent)
- [x] `answer_customer` schema

### E8.2 — Reviewer and use case [M] · tag `etapa-8.2`
- [x] `ReviewLoop` with at most 2 revisions and escalation by code
- [x] `AnswerCustomer` returning `Flow<AgentEvent>`, one per question

### E9.1 — Events, trace and cost [D] · tag `etapa-9.1`
- [x] Guide `docs/stages/9-1-trace.md`
- [x] `runId` and `parentRunId` on events, parent propagated through the coroutine context
- [x] `TraceReport` (tree, cost per agent, slowest) and a JSON trace file (`TraceFiles`)
- [x] No global state

### E9.2 — Evals [M + D] · tag `etapa-9.2`
- [x] Golden dataset of 15 cases, brand-free (with a leak check, `must_not_contain`)
- [x] Scoring and a report with the 6 metrics (routing, groundedness, facts, sources, escalation precision and recall) + a leak metric, latency and tokens
- [x] `EvalRunner` keeps going after a case fails (each case with its own empty tickets file and a pinned reference date)
- [x] Real report in `reports/` (baseline: `eval-2026-10-17-2043.md`; run with `./gradlew :eval:run`)

### E10 — Phase 1 wrap-up [M] · tag `fase-1`
- [x] README with a diagram, how to run, metrics and a trace excerpt (ADR 0002 comes with stage 5)
- [x] ADRs 0001 to 0003
- [x] Spec success criteria checked and every tag exists

---

## Phase 2 — Interface (own spec and plan, after phase 1)

### E11 — Ktor server
- [x] Phase 2 spec and plan
- [x] `server`: `POST /chat` with event streaming, key only on the server
- [x] HTTP contract test

### E12 — Compose Multiplatform app (a small app for real interactions)
- [x] Targets chosen (Desktop first; Android, iOS and Web later, checking current maturity)
- [x] `contract` enabled for the app's targets
- [x] Chat where a person types real questions and sees the answer, cited sources, an escalated badge and the live trace tree (the same `AgentEvent` stream the terminal prints)
- [ ] Server reachable from the clients (emulator, simulator, real device)

---

## Final delivery

### E13 — Teaching presentation
- [x] Format defined (deck or page)
- [x] Stage titles use the real concept name with the acronym spelled out, then a plain-language subtitle (see `docs/presentation/glossary.md`)
- [x] One block per stage: concept, analogy, diagram, real code from the tag, demo, what to observe
- [x] Complete architecture and evaluation numbers
- [ ] GIF or video of the demo

### E14 — Article to publish (last item)
- [ ] Platform and audience chosen
- [x] Outline: problem, architecture, what was built stage by stage, evaluation numbers, lessons learned
- [x] Draft with real code excerpts taken from the tags
- [ ] Images: architecture diagram, demo GIF, metrics table
- [ ] Review by Diego and adjustments
- [ ] Publishing (only with Diego's confirmation) and a link in the README
