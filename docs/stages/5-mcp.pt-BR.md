# Etapa 5 — MCP com o SDK oficial

[English](5-mcp.md) | [Português](5-mcp.pt-BR.md)

## Objetivo

Tirar as ferramentas de pedido e de ticket de dentro do processo do agente. Elas viram **servidores MCP separados**, e o agente as descobre e chama pelo Model Context Protocol, usando o **SDK oficial de Kotlin** (`io.modelcontextprotocol:kotlin-sdk`, versão `0.15.0`, que já está em `gradle/libs.versions.toml` como `mcp-sdk`). Para o `Agent` nada muda: uma ferramenta remota é só mais um `Tool`. Você escreve esta etapa.

| Peça | Etapa 4 | Etapa 5 |
|---|---|---|
| `Agent` e porta `Tool` | real | real, sem mudar |
| Ferramentas de pedido e ticket | objetos locais | **as mesmas classes, servidas por MCP** |
| `ToolProvider` | `LocalToolProvider` | **`McpToolProvider`** (novo) |
| Protocolo | nenhum | **MCP sobre stdio, pelo SDK** |

## Conceito

MCP é JSON-RPC sobre um transporte. Com **stdio**, o cliente sobe o servidor como processo filho e os dois conversam pelo stdin e stdout dele, uma mensagem por linha. O fluxo é sempre o mesmo:

1. `initialize`: o aperto de mão, em que cada lado diz o que suporta.
2. `tools/list`: o cliente pergunta quais ferramentas existem (nome, descrição, JSON Schema).
3. `tools/call`: o cliente pede ao servidor para rodar uma ferramenta.

Duas regras que quebram tudo se esquecidas:

- **Num servidor stdio, o stdout é o protocolo.** Um `println` perdido corrompe a conversa. Mande logs para o stderr e redirecione `System.out` para `System.err` antes de o servidor subir.
- **O servidor não fica vivo sozinho.** `Server.createSession(transport)` volta assim que o transporte conecta. O `main` precisa esperar a sessão fechar; senão o processo termina na hora.

## Fatos do SDK (conferidos nos fontes do SDK)

| O quê | Forma exata |
|---|---|
| Artefatos | `io.modelcontextprotocol:kotlin-sdk` (guarda-chuva), `kotlin-sdk-client`, `kotlin-sdk-server` |
| Alvos | JVM, JS, Wasm e Native (usamos JVM) |
| Servidor | `Server(serverInfo: Implementation, options: ServerOptions)` |
| Registrar ferramenta | `server.addTool(name, description, inputSchema: ToolSchema, ..., handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult)` |
| Esquema da ferramenta | `ToolSchema(properties: JsonObject?, required: List<String>?)` |
| Requisição | `CallToolRequest.name: String`, `CallToolRequest.arguments: JsonObject?` |
| Resultado | `CallToolResult(content: List<ContentBlock>, isError: Boolean?)`, com `TextContent(text = ...)` |
| Transporte do servidor | `StdioServerTransport(inputStream = System.in.asSource().buffered(), outputStream = System.out.asSink().buffered())` |
| Iniciar sessão | `val session = server.createSession(transport)`; `session.onClose { ... }` |
| Cliente | `Client(clientInfo = Implementation(name, version))` |
| Transporte do cliente | `StdioClientTransport(input: Source, output: Sink, error: Source? = null)`: recebe os streams de um processo que **você** inicia |
| Usar o cliente | `client.connect(transport)`, `client.listTools().tools`, `client.callTool(name, arguments: Map<String, Any?>): CallToolResult` |
| Streams | `kotlinx.io`: `InputStream.asSource().buffered()` e `OutputStream.asSink().buffered()` |
| Pacotes | `io.modelcontextprotocol.kotlin.sdk.server.*`, `...sdk.client.*`, `...sdk.types.*` |

Pontos de atenção:

- **Não chame `transport.start()`** no transporte do cliente. Um exemplo de KDoc chama, mas a classe não tem esse método; é o `client.connect(transport)` que o inicializa.
- `Tool.description` e `isError` são anuláveis no SDK. O nosso `ToolSpec` tem versões não nulas, então faça o mapeamento.
- O que não foi confirmado aqui, para checar nos fontes do SDK quando chegar lá: os nomes dos campos de `ToolAnnotations` (para levar as nossas marcas `readOnly` e `destructive`), se o `kotlinx-io-core` precisa ser declarado explicitamente, e o membro exato de `TextContent` que guarda o texto.

## Desenho

Quatro peças pequenas, todas em `adapters` (JVM), mais as duas funções `main` dos servidores:

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

E no `core`, o modelo de configuração (dados `@Serializable` simples, sem SDK):

```kotlin
@Serializable data class McpServerConfig(val name: String, val command: List<String>, val env: Map<String, String> = emptyMap())
@Serializable data class McpServersConfig(val servers: List<McpServerConfig>)
```

Por que essa divisão: o `McpToolServer` transforma **qualquer** lista de `Tool` em servidor MCP, então `mcp-orders` e `mcp-tickets` ficam com poucas linhas cada. `McpConnection`/`McpConnector` escondem o SDK e o processo filho, o que permite testar o `McpToolProvider` de forma rápida, offline e com MockK, e deixa o código real do SDK numa classe só (`StdioMcpConnector`).

Regras de comportamento:

- `McpToolServer`: redirecione `System.out` para `System.err` primeiro; um `server.addTool` por `Tool`, convertendo o `ToolSpec.inputSchema` em `ToolSchema` e o resultado em `CallToolResult(content = listOf(TextContent(...)), isError = ...)`; `arguments` ausente vira objeto vazio; espere a sessão fechar com um `CompletableDeferred` completado no `session.onClose`.
- `mcp-orders` expõe `get_order_status` e `track_shipment`; `mcp-tickets` expõe `create_ticket`. Os dois leem `DATA_DIR` (padrão `data`) e montam os repositórios JSON da etapa 3. O `main` deles é `suspend fun main()`.
- `StdioMcpConnector`: inicie o processo com `ProcessBuilder(command)`, juntando o `env`; embrulhe `process.inputStream`/`outputStream` com `asSource().buffered()`/`asSink().buffered()`; conecte dentro de `withTimeout(startTimeoutMs)`; qualquer falha vira `McpServerException` com o nome do servidor, e o processo é destruído. `close()` fecha o cliente e destrói o processo.
- `McpToolProvider.tools()`: conecta a cada servidor **uma vez** (de forma preguiçosa, na primeira chamada), lista as ferramentas e embrulha cada uma num `Tool`. Dois servidores com o mesmo nome de ferramenta é erro de configuração: lance `McpServerException`.
- Rodar uma ferramenta remota: `withTimeout(callTimeoutMs) { connection.callTool(name, args) }`. Estouro de tempo, servidor morto ou qualquer exceção vira `ToolResult(content = "error: ...", isError = true)`: o agente tem que receber um erro e seguir, nunca uma queda nem uma espera infinita. `CancellationException` deve ser relançada.
- `close()` fecha todas as conexões.

## Configuração

`config/mcp-servers.json` lista os servidores. O comando aponta para o script de partida que o plugin `application` do Gradle gera (`./gradlew installDist`):

```json
{
  "servers": [
    { "name": "orders", "command": ["mcp-orders/build/install/mcp-orders/bin/mcp-orders"], "env": { "DATA_DIR": "data" } }
  ]
}
```

O critério de sucesso desta etapa: **adicionar o servidor de tickets só editando este arquivo**, sem mudar código no agente. O `Agent` nunca fica sabendo que a ferramenta mora em outro lugar.

## Testes (escreva primeiro)

Uma classe de teste por classe de produção, MockK nos colaboradores, nomes `given … when … then should …`, sem comentários.

**`McpToolProviderTest`** (`adapters/src/jvmTest`, com `McpConnector` e `McpConnection` simulados):

- given two servers when listing tools then should return the tools of both
- given a remote tool when running it then should delegate to the connection with the same name and arguments
- given a call that exceeds the timeout when running a tool then should return an error result instead of hanging (use o tempo virtual do `runTest` e um `delay` na conexão simulada)
- given a connection that throws when running a tool then should return an error result
- given a server that fails to connect when listing tools then should throw McpServerException naming that server
- given two servers exposing the same tool name when listing tools then should throw McpServerException
- given tools listed twice when listing again then should connect only once
- given a provider when closing it then should close every connection

**`McpToolServerTest`** (`adapters/src/jvmTest`): conecte um `Client` do SDK ao servidor **no mesmo processo** por streams em pipe (sem processo filho), com mocks de `Tool`:

- given a tool when a client lists tools then should expose its name, description and input schema
- given a tool when a client calls it then should pass the arguments and return its text
- given a tool that returns an error result when a client calls it then should mark the MCP result as an error
- given a call without arguments when a client calls the tool then should run it with an empty object

**`StdioMcpConnectorTest`** (no módulo `mcp-orders`, para o classpath do teste conter o servidor): suba o servidor real com `java -cp <java.class.path> dsupport.mcp.orders.MainKt` e `DATA_DIR` apontando para `../data`:

- given the real orders server when listing tools then should find get_order_status and track_shipment
- given the real orders server when calling get_order_status then should return the order of the data file
- given a command that does not exist when connecting then should throw McpServerException
- given a server that dies while connected when calling a tool then should return an error result through the provider

**`McpServersConfigTest`** (`core`): given a JSON file with two servers when decoding then should keep names, commands and environments.

## Rodar

```bash
./gradlew :adapters:jvmTest :mcp-orders:test :core:jvmTest
./gradlew installDist
```

Depois experimente um servidor à mão, para ver o protocolo: `echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | mcp-orders/build/install/mcp-orders/bin/mcp-orders` (ele vai exigir o `initialize` antes; é exatamente essa a ideia).

## O que observar

- As três requisições de uma sessão, em ordem: `initialize`, `tools/list`, `tools/call`.
- Que nada além de mensagens do protocolo aparece no stdout do servidor.
- O código do agente: tem que ficar idêntico ao da etapa 4.

## Exercícios (opcionais)

- Prefixe os nomes das ferramentas com o nome do servidor quando dois servidores colidirem, em vez de falhar.
- Reinicie um servidor que morreu, uma vez, antes de devolver o erro.
- Exponha a busca na base de conhecimento como um terceiro servidor MCP e compare com a ferramenta local. Quando cada um é melhor?

## ADR

Escreva `docs/adr/0002-mcp-for-external-systems.md` (e o gêmeo `.pt-BR.md`): sistemas externos com dono próprio ficam atrás de MCP em processos separados; a lógica interna do agente continua local; a porta `Tool` esconde a diferença.

## Como explicar na entrevista

"Movi as ferramentas de pedido e de ticket para servidores MCP separados, usando o SDK oficial de Kotlin. O agente só conhece a porta `Tool`, então uma ferramenta remota é indistinguível de uma local. Embrulhei o SDK atrás de uma interface de conector, para testar o provedor com mocks, e coloquei tempo limite e resultado de erro para que um servidor morto nunca trave nem derrube o agente. Adicionar um servidor novo é mudança de configuração, não de código."
