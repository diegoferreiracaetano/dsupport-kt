# Stage 5 — MCP with the official SDK

[English](5-mcp.md) | [Português](5-mcp.pt-BR.md)

## Goal

Move the order and ticket tools out of the agent process. They become **separate MCP servers**, and the agent discovers and calls them through the Model Context Protocol, using the **official Kotlin SDK** (`io.modelcontextprotocol:kotlin-sdk`, version `0.15.0`, already in `gradle/libs.versions.toml` as `mcp-sdk`). To the `Agent` nothing changes: a remote tool is just another `Tool`. You write this stage.

| Piece | Stage 4 | Stage 5 |
|---|---|---|
| `Agent` and `Tool` port | real | real, unchanged |
| Order and ticket tools | local objects | **same classes, served over MCP** |
| `ToolProvider` | `LocalToolProvider` | **`McpToolProvider`** (new) |
| Protocol | none | **MCP over stdio, via the SDK** |

## Concept

MCP is JSON-RPC over a transport. With **stdio** the client starts the server as a child process and they talk through its stdin and stdout, one message per line. The flow is always the same:

1. `initialize`: the handshake, where both sides announce what they support.
2. `tools/list`: the client asks which tools exist (name, description, JSON Schema).
3. `tools/call`: the client asks the server to run one tool.

Two rules that break everything if forgotten:

- **On a stdio server, stdout is the protocol.** A stray `println` corrupts the conversation. Send logs to stderr, and redirect `System.out` to `System.err` before the server starts.
- **The server does not stay alive by itself.** `Server.createSession(transport)` returns as soon as the transport is connected. The `main` must wait until the session closes, otherwise the process exits immediately.

## SDK facts (checked against the SDK sources)

| What | Exact shape |
|---|---|
| Artifacts | `io.modelcontextprotocol:kotlin-sdk` (umbrella), `kotlin-sdk-client`, `kotlin-sdk-server` |
| Targets | JVM, JS, Wasm and Native (we use JVM) |
| Server | `Server(serverInfo: Implementation, options: ServerOptions)` |
| Register a tool | `server.addTool(name, description, inputSchema: ToolSchema, ..., handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult)` |
| Tool schema | `ToolSchema(properties: JsonObject?, required: List<String>?)` |
| Request | `CallToolRequest.name: String`, `CallToolRequest.arguments: JsonObject?` |
| Result | `CallToolResult(content: List<ContentBlock>, isError: Boolean?)`, with `TextContent(text = ...)` |
| Server transport | `StdioServerTransport(inputStream = System.in.asSource().buffered(), outputStream = System.out.asSink().buffered())` |
| Start a session | `val session = server.createSession(transport)`; `session.onClose { ... }` |
| Client | `Client(clientInfo = Implementation(name, version))` |
| Client transport | `StdioClientTransport(input: Source, output: Sink, error: Source? = null)`: it takes the streams of a process **you** start |
| Use the client | `client.connect(transport)`, `client.listTools().tools`, `client.callTool(name, arguments: Map<String, Any?>): CallToolResult` |
| Streams | `kotlinx.io`: `InputStream.asSource().buffered()` and `OutputStream.asSink().buffered()` |
| Packages | `io.modelcontextprotocol.kotlin.sdk.server.*`, `...sdk.client.*`, `...sdk.types.*` |

Careful points:

- **Do not call `transport.start()`** on the client transport. A KDoc example does, but the class has no such method; `client.connect(transport)` initializes it.
- `Tool.description` and `isError` are nullable in the SDK. Our `ToolSpec` has non-null versions, so map them.
- Things not confirmed here, to check in the SDK sources when you hit them: the field names of `ToolAnnotations` (to carry our `readOnly` and `destructive` flags), whether `kotlinx-io-core` needs to be declared explicitly, and the exact member of `TextContent` that holds the text.

## Design

Four small pieces, all in `adapters` (JVM), plus the two server `main` functions:

```kotlin
class McpToolServer(name: String, version: String, tools: List<Tool>) {
    suspend fun serve(input: InputStream = System.`in`, output: OutputStream = System.out)
}

interface McpConnection : AutoCloseable {
    suspend fun listTools(): List<ToolSpec>
    suspend fun callTool(name: String, args: JsonObject): ToolResult
}

fun interface McpConnector {
    suspend fun connect(server: McpServerConfig): McpConnection
}

class StdioMcpConnector(private val startTimeoutMs: Long = 10_000) : McpConnector

class McpToolProvider(
    private val config: McpServersConfig,
    private val connector: McpConnector = StdioMcpConnector(),
    private val callTimeoutMs: Long = 15_000,
) : ToolProvider, AutoCloseable

class McpServerException(val server: String, message: String, cause: Throwable? = null) : Exception(message, cause)
```

And in `core`, the configuration model (plain `@Serializable` data, no SDK):

```kotlin
@Serializable data class McpServerConfig(val name: String, val command: List<String>, val env: Map<String, String> = emptyMap())
@Serializable data class McpServersConfig(val servers: List<McpServerConfig>)
```

Why the split: `McpToolServer` wraps **any** list of `Tool` as an MCP server, so `mcp-orders` and `mcp-tickets` shrink to a few lines each. `McpConnection`/`McpConnector` hide the SDK and the child process, which lets `McpToolProvider` be tested fast, offline and with MockK, and keeps the real SDK code in one class (`StdioMcpConnector`).

Behaviour rules:

- `McpToolServer`: redirect `System.out` to `System.err` first; one `server.addTool` per `Tool`, converting `ToolSpec.inputSchema` to `ToolSchema` and the result to `CallToolResult(content = listOf(TextContent(...)), isError = ...)`; a missing `arguments` is an empty object; wait for the session to close with a `CompletableDeferred` completed in `session.onClose`.
- `mcp-orders` exposes `get_order_status` and `track_shipment`; `mcp-tickets` exposes `create_ticket`. Both read `DATA_DIR` (default `data`) and build the JSON gateways from stage 3. Their `main` is `suspend fun main()`.
- `StdioMcpConnector`: start the process with `ProcessBuilder(command)`, merging `env`; wrap `process.inputStream`/`outputStream` with `asSource().buffered()`/`asSink().buffered()`; connect inside `withTimeout(startTimeoutMs)`; any failure becomes `McpServerException` naming the server, and the process is destroyed. `close()` closes the client and destroys the process.
- `McpToolProvider.tools()`: connects to every server **once** (lazily, on the first call), lists their tools and wraps each in a `Tool`. Two servers exposing the same tool name is a configuration error: throw `McpServerException`.
- Running a remote tool: `withTimeout(callTimeoutMs) { connection.callTool(name, args) }`. A timeout, a dead server or any exception becomes `ToolResult(content = "error: ...", isError = true)`: the agent must receive an error and go on, never a crash or a hang. `CancellationException` must be rethrown.
- `close()` closes every connection.

## Configuration

`config/mcp-servers.json` lists the servers. The command points at the start script that Gradle's `application` plugin generates (`./gradlew installDist`):

```json
{
  "servers": [
    { "name": "orders", "command": ["mcp-orders/build/install/mcp-orders/bin/mcp-orders"], "env": { "DATA_DIR": "data" } }
  ]
}
```

The success criterion of this stage: **add the tickets server only by editing this file**, with no code change in the agent. The `Agent` never learns the tool lives elsewhere.

## Tests (write them first)

One test class per production class, MockK for collaborators, names `given … when … then should …`, no comments.

**`McpToolProviderTest`** (`adapters/src/jvmTest`, with a mocked `McpConnector` and `McpConnection`):

- given two servers when listing tools then should return the tools of both
- given a remote tool when running it then should delegate to the connection with the same name and arguments
- given a call that exceeds the timeout when running a tool then should return an error result instead of hanging (use `runTest` virtual time and a `delay` in the mocked connection)
- given a connection that throws when running a tool then should return an error result
- given a server that fails to connect when listing tools then should throw McpServerException naming that server
- given two servers exposing the same tool name when listing tools then should throw McpServerException
- given tools listed twice when listing again then should connect only once
- given a provider when closing it then should close every connection

**`McpToolServerTest`** (`adapters/src/jvmTest`): connect an SDK `Client` to the server **in the same process** through piped streams (no child process), with `Tool` mocks:

- given a tool when a client lists tools then should expose its name, description and input schema
- given a tool when a client calls it then should pass the arguments and return its text
- given a tool that returns an error result when a client calls it then should mark the MCP result as an error
- given a call without arguments when a client calls the tool then should run it with an empty object

**`StdioMcpConnectorTest`** (in the `mcp-orders` module, so the test classpath contains the server): start the real server with `java -cp <java.class.path> dsupport.mcp.orders.MainKt` and `DATA_DIR` pointing at `../data`:

- given the real orders server when listing tools then should find get_order_status and track_shipment
- given the real orders server when calling get_order_status then should return the order of the data file
- given a command that does not exist when connecting then should throw McpServerException
- given a server that dies while connected when calling a tool then should return an error result through the provider

**`McpServersConfigTest`** (`core`): given a JSON file with two servers when decoding then should keep names, commands and environments.

## Run

```bash
./gradlew :adapters:jvmTest :mcp-orders:test :core:jvmTest
./gradlew installDist
```

Then try a server by hand, to see the protocol: `echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | mcp-orders/build/install/mcp-orders/bin/mcp-orders` (it will ask for `initialize` first; that is the point).

## What to observe

- The three requests of a session, in order: `initialize`, `tools/list`, `tools/call`.
- That nothing but protocol messages appears on the server's stdout.
- The agent code: it must be identical to stage 4.

## Exercises (optional)

- Prefix the tool names with the server name when two servers collide, instead of failing.
- Restart a server that died, once, before returning the error.
- Expose the knowledge base search as a third MCP server and compare it with the local tool. When is each better?

## ADR

Write `docs/adr/0002-mcp-for-external-systems.md` (and its `.pt-BR.md` twin): external systems with their own owner go behind MCP in separate processes; internal agent logic stays local; the `Tool` port hides the difference.

## How to explain it in an interview

"I moved the order and ticket tools into separate MCP servers using the official Kotlin SDK. The agent only knows the `Tool` port, so a remote tool is indistinguishable from a local one. I wrapped the SDK behind a connector interface so the provider is unit-tested with mocks, and I added timeouts and error results so a dead server can never hang or crash the agent. Adding a new server is a configuration change, not a code change."
