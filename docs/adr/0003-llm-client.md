# ADR 0003 — LLM client

[English](0003-llm-client.md) | [Português](0003-llm-client.pt-BR.md)

Status: accepted (stage 2)

## Context

The `core` only knows the `LlmClient` port. It remains to decide how the first adapter, the Anthropic one, talks to the API, and how it handles the model's reasoning and structured output.

## Decisions

1. **Plain HTTP with Ktor, in `commonMain`, instead of the official SDK.** Anthropic's official SDK for Kotlin is the Java one, which only runs on the JVM, and the `adapters` module is multiplatform. The cost is maintaining the JSON mapping by hand; the benefit is portability and a transparent protocol. If the SDK ever becomes preferable, it goes into `jvmMain` behind the same port, without touching the `core`.
2. **`thinking` always disabled.** Claude Haiku 5.5 turns reasoning on by default, and the reasoning blocks would have to go back into the history unchanged. The neutral `Message` does not hold provider-specific blocks, so the client sends `thinking: {"type": "disabled"}` and fails with `LlmApiException` if a reasoning block shows up. Consequence: models that cannot have reasoning turned off, such as Claude Opus 5.5, return a 400 with this client.
3. **Structured output through `output_config.format` (`json_schema`), not a forced `tool_choice`.** Forcing a tool returns a 400 on several models. The native format works on all of them, and the answer arrives as JSON text that the client turns into an object. Schema limitation: no `minimum`/`maximum`, so ranges such as `confidence` between 0 and 1 are validated in our own code.
4. **Retry only on 429 and 5xx**, up to `maxRetries` (2 by default), with a growing wait (500 ms, 1 s). 400, 401, 403 and 404 fail immediately, because retrying does not fix them.
5. **Consecutive messages with the same role become one**, with the `tool_result` blocks before the text, because the API requires alternating roles and wants the results of one turn together.

## Consequences

- Switching providers means writing another adapter that passes `LlmClientContract`; the `core` does not change.
- Using a model that requires reasoning to stay on calls for an evolution of `Message` (an opaque per-provider field) before it can work.
