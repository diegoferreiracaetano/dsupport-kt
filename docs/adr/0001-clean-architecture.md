# ADR 0001 — Clean Architecture in modules

[English](0001-clean-architecture.md) | [Português](0001-clean-architecture.pt-BR.md)

Status: accepted (stage 0)

## Context

The assistant must survive changes of model provider, data source, protocol and interface without rewriting its logic.

## Decisions

1. **Dependencies point inward.** `contract` (vocabulary) ← `core` (ports, agent loop, guards, review, use case) ← `adapters` (Anthropic, RAG, JSON files, MCP) ← applications (`cli`, `eval`, MCP servers).
2. **Ports live in the core.** `LlmClient`, `Tool`, `ToolProvider` and the gateways are interfaces the core owns; adapters implement them.
3. **`contract` and `core` are multiplatform (JVM and Wasm)**, so a future interface can share them. Anything that needs the JVM lives in `adapters/jvmMain` or the applications.
4. **Tests use doubles at the ports.** No test calls a real model; the evaluation is the only place that does, on demand.

## Consequences

- Swapping the LLM, the knowledge base or the tool transport is a new adapter, never a change in the core.
- The Wasm build of `contract` and `core` is checked in CI to keep the core portable.
