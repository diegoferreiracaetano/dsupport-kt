# Stages 11 and 12 — A server and an app for real interactions

[English](11-12-interface.md) | [Português](11-12-interface.pt-BR.md)

## Goal

Let a person talk to the assistant. A **Ktor server** exposes the `AnswerCustomer` use case over HTTP and a **Compose Desktop app** shows the conversation, the cited sources, an escalated badge and the live trace tree.

## How it works

```text
app (Compose)  --POST /chat {"question": "..."}-->  server (Ktor)  -->  AnswerCustomer  -->  model
      ^                                                   |
      +------ text/event-stream: one AgentEvent per frame ---+
```

- **Server-Sent Events.** The server writes `data: <AgentEvent JSON>` for every event and flushes it, so the app can draw the trace while the assistant is still working. A failure in the middle of the stream becomes an `event: error` frame. `GET /health` answers `ok`.
- **The key stays on the server.** Only the server reads `ANTHROPIC_API_KEY`; the app only knows `DSUPPORT_SERVER` (default `http://localhost:8080`).
- **One protocol, two sides.** `ChatRequest`, the frames and `SseParser` live in `contract`, so the server and the app cannot drift apart, and it is tested once.
- **The app is layered like the core.** `Assistant` is the port, `ChatClient` the adapter, `ChatViewModel` the state, `ChatScreen` the drawing. Everything but the drawing is tested with doubles; the drawing is checked by rendering it headlessly to a PNG.

- **Look.** Geist type (OFL, bundled in `app/src/main/resources/fonts`), one cobalt accent, light and dark themes following the system, one corner radius, an empty state with suggestions and a trace panel that can be hidden.
- **Language.** The screen has a PT/EN selector, Portuguese by default (`Strings`, `Language`). The assistant answers in the language the customer writes in; the orchestrator prompt says so. The trace stays in English, it is technical output.

## Run

```bash
set -a; source .env; set +a
./gradlew :server:run
```

In another terminal:

```bash
./gradlew :app:run
```

Optional: `MCP_SERVERS_FILE=config/mcp-servers.json` (after `./gradlew installDist`) makes the server use the MCP tools. `PORT` changes the server port.

## Tests

- `ServerSentEventsTest` (`contract`): framing and parsing, including error frames.
- `ChatModuleTest` (`server`): streaming, validation (blank, too long, not JSON), mid-stream failure, health.
- `ChatClientTest` (`app`, `MockEngine`): events in order, request shape, error frame, rejected question, unreachable server.
- `ChatViewModelTest` (`app`): messages, live trace, completion, failures, blank and concurrent questions.
- `ChatScreenRenderTest` (`app`): renders the finished and the running screens to `app/build/render/`.

No test calls the model API.

## Next targets

`contract` and `core` already compile for the JVM and Wasm. Android, iOS and Web clients can reuse the protocol and the view model once the app becomes a multiplatform module; that is left as an evolution.
