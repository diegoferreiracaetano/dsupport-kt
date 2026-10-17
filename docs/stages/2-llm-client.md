# Stage 2 — The real Claude answers

[English](2-llm-client.md) | [Português](2-llm-client.pt-BR.md)

## Goal

Replace the mock model used in tests with a client that calls the Anthropic API. The `Agent` and the tools **do not change**: only a new adapter appears behind the `LlmClient` port. You write this adapter.

| Piece | Stage 1 | Stage 2 |
|---|---|---|
| Agentic loop (`Agent`) | real | real, unchanged |
| `LlmClient` | MockK mock (in tests) | **`AnthropicLlmClient` (real)** |
| Tools | tests only | tests only |

Location: the `adapters` module, in `commonMain` (multiplatform code, no `java.*`).

## Concept: the tool use protocol

The API has no memory: **every call sends the whole history** and the list of tools. The model never executes anything, it only asks. Your code executes, and the result goes back as the next message.

How our neutral vocabulary becomes the API JSON:

| In Kotlin (`core`) | In the API | Note |
|---|---|---|
| `Message.User(text)` | `{"role":"user","content":"text"}` | |
| `Message.AssistantText(text)` | `{"role":"assistant","content":"text"}` | |
| `Message.AssistantToolCalls(calls)` | `{"role":"assistant","content":[{"type":"tool_use","id":..,"name":..,"input":{..}}]}` | one block per call |
| `Message.ToolResult(...)` | block `{"type":"tool_result","tool_use_id":..,"content":"..","is_error":true?}` inside a **`user`** message | a tool result comes back as `user` |

Three rules that break the request if forgotten (and that interviewers love):

1. **The `id` links request and result.** The `tool_result` returns the same `id` as the `tool_use`.
2. **Results of the same turn go in a single message.** If the model asked for two tools, both `tool_result` blocks sit in the **same** `user` message. Splitting them teaches the model to stop asking in parallel.
3. **Roles must alternate.** Two `user` messages in a row (for example a tool result and then the text "return the final answer") become **one** message, with the `tool_result` blocks first and the text after.

### The request

`POST https://api.anthropic.com/v1/messages`, with the headers `x-api-key`, `anthropic-version: 2023-06-01` and `content-type: application/json`.

```json
{
  "model": "claude-haiku-5-5",
  "max_tokens": 4096,
  "system": "You are a support assistant.",
  "thinking": { "type": "disabled" },
  "tools": [
    { "name": "get_order_status", "description": "...", "input_schema": { "type": "object", "properties": { "order_id": { "type": "string" } }, "required": ["order_id"] } }
  ],
  "messages": [
    { "role": "user", "content": "Where is my order 1042?" }
  ]
}
```

### The response

```json
{
  "content": [
    { "type": "text", "text": "Let me check." },
    { "type": "tool_use", "id": "toolu_01A", "name": "get_order_status", "input": { "order_id": "1042" } }
  ],
  "stop_reason": "tool_use",
  "usage": { "input_tokens": 312, "output_tokens": 54 }
}
```

Read the blocks **by `type`**, never by position. If there is a `tool_use`, return `LlmReply.ToolCalls` with all the `tool_use` blocks; otherwise join the `text` blocks into `LlmReply.Text`. Token usage comes from `usage`.

## Three design decisions (already made, recorded in the ADR)

**1. Plain HTTP with Ktor, in `commonMain`, instead of the official SDK.** Anthropic's official SDK for Kotlin is the Java one, which only runs on the JVM. Since `adapters` has multiplatform code and the goal of the stage is to understand the protocol, we write the client on top of Ktor's `HttpClient`. The cost is maintaining the JSON mapping; the benefit is portability and transparency. If you ever want the SDK, it goes into `jvmMain` behind the same port, and the `core` never notices.

**2. `thinking: {"type":"disabled"}` always.** On Claude Haiku 5.5 reasoning is **on by default**: a response can start with `thinking` blocks, and those blocks would have to go back into the history unchanged. Our neutral `Message` does not hold provider-specific reasoning blocks, so we turn reasoning off (accepted on Haiku 5.5 with the default `effort`). Consequence recorded in the ADR: models that **cannot** have it disabled (such as Claude Opus 5.5) return a 400 with this client. If a response carries a `thinking` block anyway, throw `LlmApiException` with a clear message instead of silently ignoring it.

**3. Structured output through `output_config.format`, not a forced `tool_choice`.** In the study, the final format became a forced "tool". That now returns a 400 on several models (Claude Opus 5.5, Sonnet 5.5, Fable 5.1). The path that works everywhere is:

```json
"output_config": {
  "format": { "type": "json_schema", "schema": { "type": "object", "properties": { "...": {} }, "required": ["..."], "additionalProperties": false } }
}
```

The answer arrives as **JSON text** in a `text` block; you parse it into a `JsonObject`. Schema limits: every object needs `additionalProperties: false`, and there is no `minimum`/`maximum`/`minLength` (so range validation, such as `confidence` between 0 and 1, stays in our own code). Keep `tools` declared in the structured request, because the history may contain calls to them.

## What to write

New files in `adapters/src/commonMain/kotlin/dsupport/adapters/llm/`:

```kotlin
class LlmApiException(val status: Int?, message: String) : Exception(message)

class AnthropicLlmClient(
    private val apiKey: String,
    private val modelId: String = "claude-haiku-5-5",
    private val http: HttpClient,
    private val maxTokens: Int = 4096,
    private val maxRetries: Int = 2,
    private val baseUrl: String = "https://api.anthropic.com",
    private val mapper: AnthropicMapper = AnthropicMapper(),
) : LlmClient {
    override suspend fun next(request: LlmRequest): LlmReply
    override suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject
}
```

The mapping lives in its own class, `AnthropicMapper` (file `AnthropicMapper.kt`), so it can be tested without a network and mocked with MockK:

```kotlin
class AnthropicMapper {
    fun buildRequest(modelId: String, maxTokens: Int, request: LlmRequest, schema: OutputSchema? = null): JsonObject
    fun parseReply(body: JsonObject): LlmReply
    fun parseStructured(body: JsonObject): JsonObject
}
```

Behaviour rules:

- Omit `tools` when the list is empty. Do not send `temperature`, `top_p` or `top_k` (Haiku 5.5 rejects non-default values). Do not use `tool_choice`.
- `ToolSpec.inputSchema` already is the JSON Schema: it goes straight into `input_schema`. `isError = true` becomes `"is_error": true` on the `tool_result`.
- **Retries:** status 429 and any 5xx (including 529, "overloaded") are retried up to `maxRetries` times, with a growing wait (`delay`, for example 500 ms and then 1000 ms). 400, 401, 403 and 404 are **not** retried: they throw `LlmApiException` immediately, with the status and the response body.
- A body without `content`, a `stop_reason` of `refusal`, or `max_tokens` on a structured call: `LlmApiException` with a clear message.
- Dependencies: `adapters` already has `ktor-client-core` in `commonMain` and `ktor-client-cio` in `jvmMain`. The call uses `http.post(url) { header(...); contentType(ContentType.Application.Json); setBody(json.toString()) }` and `response.bodyAsText()`. No content negotiation plugin is needed: build the JSON with `buildJsonObject`, as in the study.

## Tests (write them first)

In `adapters/src/jvmTest/kotlin/dsupport/adapters/llm/`, with Ktor's `MockEngine` (no network, no key). Test names, messages and KDoc in English, no comments:

```kotlin
private fun client(engine: MockEngine) = AnthropicLlmClient(
    apiKey = "test-key", http = HttpClient(engine), maxRetries = 2,
)
```

`AnthropicMapperTest` (mapping, no network) and `AnthropicLlmClientTest` (one test class per production class; in the client test the `AnthropicMapper` is mocked with MockK and the network with the `MockEngine`). Names follow `given … when … then should …`:

**AnthropicMapperTest**

- `given a request with system, messages and tools when building then should map them to the API JSON`
- `given a tool result in the history when building then should send a tool_result with its tool_use_id in a user message`
- `given several results of the same turn when building then should send them in a single message`
- `given a user message after tool results when building then should merge them with the tool_result first`
- `given failed and successful results when building then should set is_error only on the failed one`
- `given no tools when building then should omit tools and never send tool_choice or sampling parameters`
- `given an output schema when building then should add output_config format with that schema`
- `given several tool_use blocks when parsing a reply then should return ToolCalls with the usage tokens`
- `given several text blocks when parsing a reply then should join them into one Text reply`
- `given a JSON text when parsing a structured reply then should return the object`
- `given a body without content when parsing a reply then should throw LlmApiException`
- `given a thinking block when parsing a reply then should throw LlmApiException`
- `given a refusal when parsing then should throw LlmApiException`
- `given a cut or invalid JSON when parsing a structured reply then should throw LlmApiException`
- `given an empty content array when parsing a reply then should throw LlmApiException`

**AnthropicLlmClientTest**

- `given a request when asking for the next reply then should post the built body with the required headers`
- `given an output schema when asking for a structured reply then should build the request with it and parse the result`
- `given a 429 followed by a good response when asking for the next reply then should retry and return it`
- `given a 529 overloaded response when asking for the next reply then should retry like any 5xx`
- `given a persistent 500 when asking for the next reply then should retry up to maxRetries and throw`
- `given a 401 when asking for the next reply then should throw immediately without retrying`
- `given a body that is not JSON when asking for the next reply then should throw LlmApiException`

And the contract suite, which proves the client fulfils the `LlmClient` port (any future provider must pass it):

```kotlin
class AnthropicLlmClientContractTest : LlmClientContract() {
    override fun clientFor(scenario: Scenario): LlmClient =
        AnthropicLlmClient(apiKey = "test-key", http = HttpClient(MockEngine { respond(jsonFor(scenario), HttpStatusCode.OK, jsonHeaders) }))
}
```

Here `jsonFor(scenario)` returns the API response body for each scenario: `Scenario.Text` becomes a `text` block; `Scenario.ToolCall` becomes a `tool_use` block with the same `id`, name and arguments; `Scenario.Structured` becomes a `text` block whose content is the object's JSON.

## Run

```bash
./gradlew :adapters:jvmTest
./gradlew test :adapters:compileKotlinJvm
```

Expected: all tests pass, with no key and no network. Only afterwards, and only if you want to see the real Claude, export `ANTHROPIC_API_KEY` and make a manual call (the full assembly comes in stage 6).

## What to observe

- The JSON that `AnthropicMapper.buildRequest` produces for a conversation with a tool call: check that the `id` appears at both ends.
- How many messages go to the API when the history ends in a `ToolResult` followed by a `User`: it must be **one**.
- The number of calls the `MockEngine` receives in the 401 test (one) and in the 500 test (three: the original and two retries).

## Exercises (optional, independent)

- Make the client honour the `retry-after` header when a 429 carries one.
- Add the cached token count (`cache_read_input_tokens`) to `Usage`.
- Write a second client (any provider, or a fake one) that passes `LlmClientContract`. It is the proof that the `core` really is generic.

## ADR (record the decision)

Create `docs/adr/0003-llm-client.md` (and `0003-llm-client.pt-BR.md`) with the context, decision and consequences of the three decisions above: plain HTTP in `commonMain` instead of the official SDK (JVM only), `thinking` disabled because the neutral history does not carry reasoning blocks, and structured output through `output_config.format`.

## How to explain it in an interview

"The LLM API is stateless: I send the history and the tools on every call. The model only asks for a tool, my code runs it and returns the result with the same id, in a user message. I hid all of that behind an `LlmClient` port, so switching providers means writing a new adapter that passes the contract suite. And I made a conscious choice for structured output to use the API's native JSON format, because forcing a tool no longer works on every model."
