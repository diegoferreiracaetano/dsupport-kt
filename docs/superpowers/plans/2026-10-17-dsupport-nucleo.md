# DSupport — Fase 1 (núcleo) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o núcleo do DSupport (etapas 0 a 9.2 + fechamento): assistente de suporte multi-agente em Kotlin Multiplatform, com Clean Architecture, ferramentas locais e via MCP, LLM genérico, guardas, saída tipada, revisor, eventos/trace e avaliação com relatório versionado.

**Architecture:** Módulos Gradle com dependências apontando para dentro (`contract ◄ core ◄ adapters ◄ cli/eval/mcp-*`). O `core` só define portas e lógica pura (`commonMain`); `adapters` as implementa (Ktor client em `commonMain`, arquivos e MCP em `jvmMain`). O agente emite `AgentEvent`; trace, custo e avaliação saem desses eventos.

**Tech Stack:** Kotlin Multiplatform, JDK 21 (toolchain), Gradle (catálogo de versões), kotlinx.serialization, kotlinx.coroutines, kotlinx-datetime, Ktor client (CIO na JVM, MockEngine nos testes), `io.modelcontextprotocol:kotlin-sdk`, kotlin.test + JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-17-dsupport-nucleo-design.md`

## Global Constraints

- Toolchain da JVM: JDK 21. O JDK 26 do sistema não roda o Gradle 8.10; fixar o JVM do daemon em `gradle/gradle-daemon-jvm.properties`, sem tocar em `~/.gradle`.
- Pacote raiz: `dsupport` (`dsupport.contract`, `dsupport.core`, `dsupport.adapters`…).
- `contract` e `core` têm só `commonMain` e `commonTest`; proibido `java.*`, `File`, `ProcessBuilder` e Gson. Alvos de `contract` e `core`: `jvm` e `wasmJs` (testes só na JVM; o Wasm compila código e testes). `testing`, `adapters` e aplicações: só `jvm`.
- Serialização: `kotlinx.serialization` em todo lugar.
- LLM: o `core` não importa nada de provedor; só a porta `LlmClient`.
- Ferramenta destrutiva sem guarda que a cubra é rejeitada na montagem.
- Segredos só por variável de ambiente (`ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`); `.env.example` versionado, `.env` no `.gitignore`.
- Nenhuma marca de loja nos dados nem nos prompts.
- `data/tickets.json` volta a `[]` antes de cada commit; `traces/` no `.gitignore`; `reports/` versionado.
- Revisões do revisor: no máximo 2 (`maxRevisions = 2`); `maxTurns` padrão do agente = 5.
- **Docs:** project documents (README, checklist, ADRs, stage guides) exist in English (`name.md`) and Portuguese (`name.pt-BR.md`) and are always changed together. The spec and this plan are working documents, in Portuguese only.
- **Test style:** each test targets one production class, collaborators are MockK mocks (`coEvery`/`coVerify`), names follow `given <context> when <action> then should <result>`, in `jvmTest` because MockK is JVM-only. Ktor HTTP boundaries use `MockEngine`. The test names in this plan are descriptions: write them in that format.
- **Code style:** no comments in code (KDoc only, where it serves as documentation). Identifiers, KDoc, error messages, log text and test names in English. The test names and messages written in Portuguese in this plan are descriptions: write them in English. Customer-facing text (knowledge-base articles, agent prompts, replies) is product content and is decided separately.
- **Commits:** um commit por etapa, ao final dela, só no nome do usuário (sem `Co-Authored-By`), seguido da tag `etapa-N`. Não commitar por passo.
- **Autoria:** tarefas marcadas **[D]** são escritas por Diego, depois de um guia em `docs/stages/N-name.md` (e `.pt-BR.md`) escrito antes por Claude; Claude revisa e roda os testes. As marcadas **[M]** são feitas por Claude.
- Antes de cada tarefa, explicar o que será feito; depois dos testes, esperar a confirmação do Diego para tagear e seguir.

## Review Focus

- Mensagem do cliente sem número de pedido ou sem e-mail (ou e-mail malformado): nenhuma ferramenta de pedido é chamada e o agente pede o dado. → Tarefa 7.
- Pergunta sem artigo relevante na base: `search_kb` devolve `nothing_relevant_found`, o especialista responde `NO_ANSWER`, e o sistema escala em vez de inventar. → Tarefas 4 e 8.1.
- Servidor MCP que não sobe, cai no meio ou devolve erro: o agente recebe um `ToolResult(isError = true)` e segue; nada derruba o processo; há tempo limite por chamada. → Tarefa 5.
- Erro da API do LLM (429, 5xx, corpo vazio ou JSON malformado): erro tipado, no máximo 2 novas tentativas, depois falha com mensagem clara; na avaliação o caso conta como falha e o runner continua. → Tarefas 2 e 9.2.
- Duas chamadas paralelas a `create_ticket` para o mesmo cliente: sai um único ticket. → Tarefas 6 e 7.

---

### Task 0: Esqueleto do projeto (etapa 0) [M]

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, wrapper (`gradlew`, `gradle/wrapper/*`)
- Create: `contract/`, `core/`, `testing/`, `adapters/` (KMP) e `mcp-orders/`, `mcp-tickets/`, `cli/`, `eval/` (JVM), cada um com `build.gradle.kts`
- Create: `.gitignore`, `.env.example`, `README.md`, `.github/workflows/ci.yml`, `data/` (cópia limpa do estudo, ver Tarefa 3)
- Test: `core/src/commonTest/kotlin/dsupport/core/SmokeTest.kt`

**Interfaces:**
- Produces: build multi-módulo; módulos `:contract :core :testing :adapters :mcp-orders :mcp-tickets :cli :eval`; `./gradlew test` verde em JDK 21; job de CI `compileKotlinWasmJs`.

- [ ] **Step 1:** Resolver as versões atuais de Kotlin, Gradle, coroutines, serialization, datetime, Ktor, kotlin-sdk (MCP) e JUnit consultando Maven Central/releases; registrá-las em `libs.versions.toml`. Escolher um Gradle que rode no JDK 21 e um Kotlin compatível com ele.
- [ ] **Step 2:** Escrever `SmokeTest` (`fun `core compila e roda`() = assertEquals(2, 1 + 1)`) em `commonTest` do `core`.
- [ ] **Step 3:** Rodar `./gradlew :core:jvmTest` → FAIL (projeto ainda sem módulos).
- [ ] **Step 4:** Criar o build multi-módulo; `contract` e `core` com `jvm()` + `wasmJs { browser() }`; `testing` e `adapters` com `jvm()`; demais módulos Kotlin/JVM com plugin `application`; gerar `gradle-daemon-jvm.properties` para o JDK 21 (`./gradlew updateDaemonJvm`).
- [ ] **Step 5:** Rodar `./gradlew test :core:wasmJsTestClasses :contract:wasmJsTestClasses` → todos passam, a partir de um terminal cujo `java -version` é 26.
- [ ] **Step 6:** CI: workflow com JDK 21 rodando `./gradlew test` e a compilação Wasm. README com três linhas: o que é, como rodar os testes, requisito de JDK.
- [ ] **Step 7:** `git init`, ajustar `.gitignore`, commit único e tag `etapa-0`.

### Task 1: Vocabulário do núcleo e loop do agente (etapa 1) [M]

**Files:**
- Create: `contract/src/commonMain/kotlin/dsupport/contract/AgentEvent.kt`
- Create: `core/src/commonMain/kotlin/dsupport/core/llm/{Message,LlmClient,Usage}.kt`, `core/.../tool/{Tool,ToolSpec,ToolProvider}.kt`, `core/.../agent/{Agent,EventSink}.kt`
- Create: `testing/src/commonMain/kotlin/dsupport/testing/{Scenario,LlmClientContract}.kt`
- Test: `core/src/commonTest/kotlin/dsupport/core/agent/AgentTest.kt`

**Interfaces:**
- Produces (`contract`, `@Serializable`): `sealed interface AgentEvent` com `AgentStarted(agent: String, depth: Int, task: String)`, `ModelCalled(agent: String, inputTokens: Int, outputTokens: Int, durationMs: Long)`, `ToolStarted(agent: String, tool: String, args: String)`, `ToolFinished(agent: String, tool: String, result: String, isError: Boolean, durationMs: Long)`, `GuardActed(agent: String, guard: String, tool: String?, action: GuardAction, detail: String)`, `ReviewRound(round: Int, approved: Boolean, problems: List<String>)`, `Escalated(ticketId: String, reason: String)`, `Completed(answer: SupportAnswer)`; `enum GuardAction { DENY, MODIFY, REWRITE }`.
- Produces (`core`): `data class ToolCall(id: String, tool: String, args: JsonObject)`; `sealed interface Message { User(text); AssistantToolCalls(calls: List<ToolCall>); ToolResult(callId: String, tool: String, content: String, isError: Boolean); AssistantText(text) }`; `data class Usage(inputTokens: Int, outputTokens: Int)`; `data class LlmRequest(system: String, messages: List<Message>, tools: List<ToolSpec>)`; `sealed interface LlmReply { usage: Usage; Text(text, usage); ToolCalls(calls, usage) }`; `data class OutputSchema(name: String, description: String, schema: JsonObject)`; `interface LlmClient { suspend fun next(request: LlmRequest): LlmReply; suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject }`; `data class ToolSpec(name, description, inputSchema: JsonObject, readOnly: Boolean = true, destructive: Boolean = false)`; `data class ToolResult(content: String, isError: Boolean = false)`; `interface Tool { val spec: ToolSpec; suspend fun run(args: JsonObject): ToolResult }`; `interface ToolProvider { suspend fun tools(): List<Tool> }`; `fun interface EventSink { suspend fun emit(event: AgentEvent); companion object { val None } }`; `class Agent(name: String, llm: LlmClient, system: String, tools: List<Tool>, events: EventSink = EventSink.None, maxTurns: Int = 5, depth: Int = 0)` com `val evidence: List<String>`, `suspend fun run(task: String): String`, `suspend fun followUp(message: String): String`, `suspend fun finish(schema: OutputSchema): JsonObject`.
- Produces (`testing`): `sealed interface Scenario`; `abstract class LlmClientContract { abstract fun clientFor(scenario: Scenario): LlmClient }` com testes de contrato. Os dublês de `LlmClient` e `Tool` nos testes do `core` são mocks do MockK.
- `SupportAnswer` é referenciado aqui; seu código nasce na Tarefa 8.1, então nesta tarefa `Completed` usa um `SupportAnswer` mínimo já em `contract` (`data class SupportAnswer(val answer: String = "")`), ampliado na 8.1.

- [ ] **Step 1:** Testes em `AgentTest`: `devolve a resposta final quando o modelo responde texto`; `executa a ferramenta pedida, devolve o resultado ao modelo e termina` (a segunda requisição contém `Message.ToolResult` com o mesmo `callId`); `ferramenta inexistente volta como ToolResult(isError=true) sem derrubar o loop`; `para em maxTurns e devolve "Limite de 5 turnos atingido. Encaminhando a um atendente."`; `emite AgentStarted, ModelCalled, ToolStarted, ToolFinished nessa ordem`.
- [ ] **Step 2:** Rodar `./gradlew :core:jvmTest --tests "dsupport.core.agent.AgentTest"` → FAIL (tipos não existem).
- [ ] **Step 3:** Implementar os tipos acima e o `Agent` (loop sequencial; o histórico guarda o pedido original do modelo; `evidence` = conteúdo dos `ToolResult`).
- [ ] **Step 4:** Escrever `LlmClientContract` (reply de texto, reply de ferramenta com id preservado, `structured` devolve objeto); o primeiro cliente a passar nele é o da Tarefa 2.
- [ ] **Step 5:** Rodar `./gradlew :core:jvmTest :contract:jvmTest :core:compileKotlinWasmJs` → PASS.
- [ ] **Step 6:** Commit único e tag `etapa-1`.

### Task 2: `AnthropicLlmClient` (etapa 2) [D]

**Files:**
- Create: `docs/stages/2-llm-client.md` (guia, Claude), `adapters/src/commonMain/kotlin/dsupport/adapters/llm/AnthropicLlmClient.kt`, `.../llm/AnthropicMapping.kt`
- Test: `adapters/src/jvmTest/kotlin/dsupport/adapters/llm/{AnthropicMappingTest,AnthropicLlmClientContractTest}.kt`

**Interfaces:**
- Consumes: `LlmClient`, `LlmRequest`, `LlmReply`, `OutputSchema`, `Message`, `ToolSpec`, `LlmClientContract` (Tarefa 1).
- Produces: `class AnthropicLlmClient(apiKey: String, modelId: String = "claude-haiku-5-5", http: HttpClient, maxRetries: Int = 2) : LlmClient`; `class LlmApiException(val status: Int?, message: String) : Exception`.

- [ ] **Step 1:** Claude escreve o guia: tool use na API, `tool_use`/`tool_result` com `id`, o resultado voltando como `user`, saída estruturada por `tool_choice` forçado, histórico completo a cada chamada.
- [ ] **Step 2:** Testes com `MockEngine` (Diego): `mapeia system, mensagens e tools para o JSON da API`; `ToolResult vira bloco tool_result com tool_use_id dentro de mensagem user, e vários resultados do mesmo turno vão numa só mensagem`; `omite tools quando não há ferramentas e nunca envia tool_choice`; `mensagens user seguidas viram uma só, com tool_result antes do texto`; `bloco thinking lança LlmApiException`; `lê tool_use em vários blocos como ToolCalls`; `structured envia output_config.format (json_schema) e lê o objeto do texto da resposta`; `429 e 5xx repetem até maxRetries e depois lançam LlmApiException`; `corpo sem content lança LlmApiException`; e `AnthropicLlmClientContractTest` estendendo `LlmClientContract` sobre o MockEngine.
- [ ] **Step 3:** Rodar `./gradlew :adapters:jvmTest --tests "dsupport.adapters.llm.*"` → FAIL.
- [ ] **Step 4:** Diego implementa `AnthropicLlmClient` e `AnthropicMapping` (Ktor client em `commonMain`, `kotlinx.serialization`); decisão SDK oficial vs HTTP direto registrada em `docs/adr/0003-cliente-llm.md`.
- [ ] **Step 5:** Rodar os mesmos testes → PASS. Claude revisa o código.
- [ ] **Step 6:** Commit único e tag `etapa-2`.

### Task 3: Ferramentas reais e repositórios (etapa 3) [M]

**Files:**
- Create: `core/.../domain/{Order,Ticket}.kt`, `core/.../port/{OrderGateway,TicketGateway}.kt`, `core/.../tools/{GetOrderStatusTool,TrackShipmentTool,CreateTicketTool,LocalToolProvider,ToolResults}.kt`
- Create: `adapters/src/jvmMain/.../persistence/{JsonOrderGateway,JsonTicketGateway}.kt`
- Create: `data/orders.json`, `data/tickets.json` (`[]`) (copiados do estudo, sem marca)
- Test: `core/src/commonTest/.../tools/ToolsTest.kt`, `adapters/src/jvmTest/.../persistence/JsonGatewaysTest.kt`

**Interfaces:**
- Produces: `data class Order(orderId: String, email: String, status: String, eta: LocalDate, carrier: String, tracking: String, total: Double, items: List<OrderItem>)`; `data class OrderItem(sku: String, name: String, qty: Int)`; `interface OrderGateway { suspend fun findOwned(orderId: String, email: String): Order? }` (e-mail errado e pedido inexistente retornam o mesmo `null`); `data class Ticket(ticketId: String, createdAt: String, status: String, summary: String, priority: String, customerEmail: String, reason: String)`; `interface TicketGateway { suspend fun create(summary: String, priority: String, customerEmail: String, reason: String): String; suspend fun findOpenByEmail(email: String): Ticket? }`; `class GetOrderStatusTool(orders: OrderGateway, today: () -> LocalDate)`, `class TrackShipmentTool(orders: OrderGateway)`, `class CreateTicketTool(tickets: TicketGateway)` (`destructive = true`, `readOnly = false`), todas `: Tool`; `class LocalToolProvider(tools: List<Tool>) : ToolProvider`; `fun ok(vararg fields: Pair<String, JsonElement?>): ToolResult` e `fun fail(error: String): ToolResult`.

- [ ] **Step 1:** Testes: `get_order_status marca atrasado e dias_de_atraso com relógio fixo` (em_transito, eta 10 dias antes de hoje → `atrasado=true`, `dias_de_atraso=10`); `pedido entregue nunca é atrasado`; `e-mail errado devolve fail("pedido_nao_encontrado_ou_email_invalido")`; `create_ticket rejeita prioridade fora de baixa|media|alta (fail("prioridade_invalida")) e resumo com menos de 10 caracteres (fail("resumo_muito_curto"))`; `create_ticket numera TCK-1001, TCK-1002…`; `JsonOrderGateway.findOwned ignora maiúsculas no e-mail`; `JsonTicketGateway.findOpenByEmail devolve o último ticket aberto`.
- [ ] **Step 2:** Rodar `./gradlew :core:jvmTest :adapters:jvmTest` → FAIL.
- [ ] **Step 3:** Implementar as ferramentas em `core` (usam só as portas) e os gateways JSON em `jvmMain`, serializando com `kotlinx.serialization`; `ToolSpec.inputSchema` tipado (JSON Schema) em cada ferramenta.
- [ ] **Step 4:** Rodar os testes → PASS e `:core:compileKotlinWasmJs` → PASS.
- [ ] **Step 5:** Commit único e tag `etapa-3`.

### Task 4: RAG (etapa 4) [M]

**Files:**
- Create: `adapters/src/commonMain/.../rag/{Chunk,Chunker,Folding,Embedder,TfIdfEmbedder,VectorKnowledgeBase,VoyageEmbedder}.kt`, `adapters/src/jvmMain/.../rag/KbLoader.kt`, `core/.../port/KnowledgeSearch.kt`, `core/.../tools/SearchKbTool.kt`
- Create: `data/kb/*.md` (devoluções, frete, pagamentos, garantia, conta, cancelamento; sem marca)
- Test: `adapters/src/jvmTest/.../rag/{ChunkerTest,TfIdfEmbedderTest,VectorKnowledgeBaseTest,VoyageEmbedderTest}.kt`, `core/src/commonTest/.../tools/SearchKbToolTest.kt`

**Interfaces:**
- Produces: `data class Hit(docId: String, title: String, snippet: String, score: Double)`; `interface KnowledgeSearch { suspend fun search(query: String, k: Int = 3): List<Hit> }`; `interface Embedder { suspend fun embedDocuments(texts: List<String>): List<DoubleArray>; suspend fun embedQuery(text: String): DoubleArray }`; `fun chunkMarkdown(docId: String, text: String): List<Chunk>`; `fun fold(text: String): String` (minúsculas, sem acentos, sem `java.text`); `class TfIdfEmbedder : Embedder`; `class VoyageEmbedder(apiKey: String, http: HttpClient, model: String = "voyage-3.5") : Embedder`; `class VectorKnowledgeBase(embedder: Embedder, chunks: List<Chunk>) : KnowledgeSearch` com `suspend fun create(...)`; `fun loadKbDocuments(dir: Path): Map<String, String>`; `class SearchKbTool(kb: KnowledgeSearch, minScore: Double = 0.25) : Tool` (devolve `fail("nothing_relevant_found")` abaixo do corte).

- [ ] **Step 1:** Testes: `chunker divide por seções "## " e prefixa o título do artigo`; `fold("Café") == "cafe"`; `consulta sobre devolução ranqueia o artigo de devoluções em primeiro`; `texto fora do vocabulário tem score 0`; `SearchKbTool abaixo de minScore devolve nothing_relevant_found`; `VoyageEmbedder envia input_type document/query e normaliza os vetores` (MockEngine).
- [ ] **Step 2:** Rodar `./gradlew :adapters:jvmTest :core:jvmTest` → FAIL.
- [ ] **Step 3:** Implementar. Tirar acentos com uma tabela de caracteres, sem `Normalizer`. Stemming simples (`take(5)`) e stopwords como no estudo.
- [ ] **Step 4:** Rodar os testes → PASS.
- [ ] **Step 5:** Commit único e tag `etapa-4`.

### Task 5: MCP com o SDK oficial (etapa 5) [D]

**Files:**
- Create: `docs/stages/5-mcp.md` e `.pt-BR.md` (guia, Claude)
- Create: `core/.../config/McpServersConfig.kt`, `config/mcp-servers.json`
- Create: `adapters/src/jvmMain/.../mcp/{McpToolServer,McpConnection,McpConnector,StdioMcpConnector,McpToolProvider,McpServerException}.kt`
- Create: `mcp-orders/src/main/kotlin/dsupport/mcp/orders/Main.kt`, `mcp-tickets/src/main/kotlin/dsupport/mcp/tickets/Main.kt` (substituem os provisórios)
- Test: `core/.../config/McpServersConfigTest.kt`, `adapters/src/jvmTest/.../mcp/{McpToolProviderTest,McpToolServerTest}.kt`, `mcp-orders/src/test/.../StdioMcpConnectorTest.kt`

**Interfaces:**
- Consumes: `Tool`, `ToolProvider`, `ToolSpec`, `ToolResult`, as ferramentas e os gateways JSON da Tarefa 3.
- Produces: `@Serializable data class McpServerConfig(name: String, command: List<String>, env: Map<String, String> = emptyMap())`; `@Serializable data class McpServersConfig(servers: List<McpServerConfig>)`; `class McpToolServer(name: String, version: String, tools: List<Tool>) { suspend fun serve(input: InputStream, output: OutputStream) }`; `interface McpConnection : AutoCloseable { suspend fun listTools(): List<ToolSpec>; suspend fun callTool(name: String, args: JsonObject): ToolResult }`; `fun interface McpConnector { suspend fun connect(server: McpServerConfig): McpConnection }`; `class StdioMcpConnector(startTimeoutMs: Long = 10_000) : McpConnector`; `class McpToolProvider(config: McpServersConfig, connector: McpConnector = StdioMcpConnector(), callTimeoutMs: Long = 15_000) : ToolProvider, AutoCloseable`; `class McpServerException(server: String, message: String, cause: Throwable? = null) : Exception`.

- [ ] **Step 1:** Claude confirma o SDK nos fontes (feito: versão 0.15.0, stdio, API de `Server`/`Client`) e escreve o guia bilíngue.
- [ ] **Step 2:** Testes (Diego), uma classe por classe de produção, MockK, `given … when … then should …`: os de `McpToolProviderTest`, `McpToolServerTest`, `StdioMcpConnectorTest` e `McpServersConfigTest` listados no guia.
- [ ] **Step 3:** Rodar `./gradlew :adapters:jvmTest :mcp-orders:test :core:jvmTest` → FAIL.
- [ ] **Step 4:** Diego implementa; `mcp-orders` e `mcp-tickets` são `suspend fun main()` de poucas linhas sobre o `McpToolServer`; `config/mcp-servers.json` lista só `orders` primeiro.
- [ ] **Step 5:** Critério 2 da spec: acrescentar `tickets` apenas editando `config/mcp-servers.json` e ver `create_ticket` aparecer em `McpToolProvider.tools()` sem mudar código de agente.
- [ ] **Step 6:** Rodar os testes → PASS e `./gradlew installDist`. Claude revisa.
- [ ] **Step 7:** ADR `0002-mcp-for-external-systems`, commit único e tag `etapa-5`.

### Task 6: Multi-agentes e paralelismo (etapa 6) [D]

**Files:**
- Create: `docs/stages/6-multi-agents.md` (guia, Claude), `core/.../agent/{SubAgentTool,Prompts,AgentsConfig,ToolCatalog}.kt`, `config/agents.json`, `cli/src/main/kotlin/dsupport/cli/{Main,Assembly}.kt`
- Modify: `core/.../agent/Agent.kt` (executa em paralelo os pedidos de ferramenta de um mesmo turno)
- Test: `core/src/commonTest/.../agent/{ParallelToolsTest,SubAgentToolTest,ToolCatalogTest}.kt`

**Interfaces:**
- Consumes: `Agent`, `Tool`, `ToolProvider`, `LocalToolProvider`, `McpToolProvider`.
- Produces: `fun subAgentTool(name: String, description: String, agent: Agent): Tool` (entrada `{ "task": string }`); `@Serializable data class AgentDef(tools: List<String>, maxTurns: Int = 4)`; `@Serializable data class AgentsConfig(agents: Map<String, AgentDef>)`; `class ToolCatalog(providers: List<ToolProvider>) { suspend fun select(names: List<String>): List<Tool> }` (nome desconhecido = erro de montagem); prompts neutros `ORCHESTRATOR_PROMPT`, `KNOWLEDGE_PROMPT`, `ORDERS_PROMPT`, `ESCALATION_PROMPT` (sem marca); `cli`: `suspend fun assemble(events: EventSink): Agent` (orquestrador com os 3 especialistas) e `Main` que lê uma pergunta e imprime a resposta.

- [ ] **Step 1:** Claude escreve o guia: subagente é uma ferramenta; contexto e ferramentas por agente; `coroutineScope` + `async` para turnos com várias chamadas; ordem dos resultados preservada.
- [ ] **Step 2:** Testes (Diego): `duas chamadas no mesmo turno rodam em paralelo` (`runTest`, cada ferramenta com `delay(1000)`, tempo virtual total = 1000, não 2000); `resultados voltam na ordem dos pedidos`; `erro em uma ferramenta não cancela a outra`; `subAgentTool executa o agente com a tarefa e devolve a resposta`; `ToolCatalog.select lança erro para nome desconhecido`; `o orquestrador montado não tem nenhuma ferramenta de dados`; `dois pedidos paralelos de create_ticket para o mesmo e-mail resultam em um ticket` (usa o `TicketGateway` em memória do teste; passa de verdade só com a Tarefa 7, então aqui fica `@Ignore` com o motivo e é ativado na 7).
- [ ] **Step 3:** Rodar `./gradlew :core:jvmTest` → FAIL.
- [ ] **Step 4:** Diego implementa; prompts migrados do estudo e neutralizados; `config/agents.json` define as ferramentas de cada especialista (lista de permissão).
- [ ] **Step 5:** Rodar `./gradlew :core:jvmTest :core:compileKotlinWasmJs` → PASS. Rodar `./gradlew :cli:run` com `ANTHROPIC_API_KEY` exportada (pelo Diego) e conferir a resposta a "Qual o prazo para devolver um produto?".
- [ ] **Step 6:** Commit único e tag `etapa-6`.

### Task 7: Guardas (etapa 7) [M]

**Files:**
- Create: `core/.../guard/{Hook,Decision,Patterns,IdentityGate,Redaction,RedactResults,TicketDedup,MentionTicket,GuardPolicy}.kt`
- Modify: `core/.../agent/Agent.kt` (aceita `hooks: List<Hook> = emptyList()` e emite `GuardActed`)
- Test: `core/src/commonTest/.../guard/GuardsTest.kt`, `GuardPolicyTest.kt`

**Interfaces:**
- Produces: `sealed interface Decision { Allow; Deny(reason: String); Modify(args: JsonObject) }`; `interface Hook { val name: String; val covers: Set<String>; suspend fun beforeTool(agent: String, tool: ToolSpec, args: JsonObject): Decision; suspend fun afterTool(agent: String, tool: ToolSpec, result: ToolResult): ToolResult; suspend fun beforeFinal(agent: String, history: List<Message>, text: String): String }` (todos com implementação padrão neutra); `object Patterns { val cpf: Regex; val card: Regex; val email: Regex }`; `class IdentityGate`, `Redaction`, `RedactResults`, `class TicketDedup(tickets: TicketGateway)`, `MentionTicket`; `object GuardPolicy { fun validate(tools: List<Tool>, hooks: List<Hook>) }` (lança se há ferramenta `destructive` sem nenhum hook que a cubra em `covers`).
- Modify `Agent`: novo parâmetro `hooks: List<Hook> = emptyList()` após `tools`.

- [ ] **Step 1:** Testes portados do estudo, agora com mocks do MockK para `LlmClient` e `Tool`: `IdentityGate bloqueia get_order_status sem e-mail e sem order_id numérico`; `Redaction remove CPF "123.456.789-00" e cartão "4111 1111 1111 1111" dos argumentos de create_ticket e não toca no customer_email`; `RedactResults limpa o resultado devolvido por um especialista`; `TicketDedup bloqueia o segundo ticket do mesmo e-mail e devolve o número existente`; `MentionTicket acrescenta "Número do seu chamado: TCK-1001." quando a resposta final não cita o ticket`; `cada guarda emite GuardActed`; `GuardPolicy.validate rejeita create_ticket sem guarda`; ativar o teste paralelo da Tarefa 6: `dois create_ticket paralelos para o mesmo e-mail → um único ticket` (a checagem e a criação ficam sob um `Mutex` no `TicketDedup`/gateway).
- [ ] **Step 2:** Rodar `./gradlew :core:jvmTest --tests "dsupport.core.guard.*"` → FAIL.
- [ ] **Step 3:** Implementar. As três expressões regulares vivem só em `Patterns`; `IdentityGate` valida `order_id` com dígitos e `email` com `Patterns.email`.
- [ ] **Step 4:** Rodar `./gradlew :core:jvmTest :core:compileKotlinWasmJs` → PASS (o teste da Tarefa 6 agora ativo).
- [ ] **Step 5:** Ligar as 5 guardas (incluindo `RedactResults`) na montagem do `cli` e chamar `GuardPolicy.validate`.
- [ ] **Step 6:** Commit único e tag `etapa-7`.

### Task 8.1: Saída tipada (etapa 8.1) [M]

**Files:**
- Modify: `contract/.../SupportAnswer.kt`
- Create: `core/.../answer/{SupportAnswerValidator,SupportAnswerSchema}.kt`
- Test: `core/src/commonTest/.../answer/SupportAnswerValidatorTest.kt`, `contract/src/commonTest/.../SupportAnswerSerializationTest.kt`

**Interfaces:**
- Produces: `@Serializable data class SupportAnswer(answer: String = "", intent: String = "other", sources: List<String> = emptyList(), confidence: Double = 0.0, escalated: Boolean = false, @SerialName("ticket_id") ticketId: String? = null)`; `val INTENTS = listOf("policy", "order", "return", "complaint", "other")`; `object SupportAnswerValidator { fun problems(answer: SupportAnswer, evidence: List<String>): List<String> }`; `val SUPPORT_ANSWER_SCHEMA: OutputSchema` (nome `answer_customer`).

- [ ] **Step 1:** Testes: `ticket que não aparece nos resultados é "inventado"` (ex.: `TCK-9999`); `confidence 1.7 é problema`; `escalated=true sem ticket_id é problema`; `ticket_id ausente do texto ao cliente é problema`; `confiança 0.3 sem escalar é problema`; `fonte citada que não aparece nos resultados é problema`; `intent fora de INTENTS é problema`; `JSON só com {"answer":"Oi"} decodifica com os padrões`; `resultado NO_ANSWER do conhecimento não produz fonte`.
- [ ] **Step 2:** Rodar `./gradlew :core:jvmTest :contract:jvmTest` → FAIL.
- [ ] **Step 3:** Implementar; `Agent.finish` já existe (Tarefa 1) e passa o histórico mais a instrução "devolva agora a sua resposta final no formato answer_customer".
- [ ] **Step 4:** Rodar os testes → PASS.
- [ ] **Step 5:** Commit único e tag `etapa-8.1`.

### Task 8.2: Revisor e caso de uso (etapa 8.2) [M]

**Files:**
- Create: `core/.../review/{Review,ReviewLoop}.kt`, `core/.../usecase/AnswerCustomer.kt`
- Test: `core/src/commonTest/.../review/ReviewLoopTest.kt`, `core/src/commonTest/.../usecase/AnswerCustomerTest.kt`

**Interfaces:**
- Produces: `@Serializable data class Review(approved: Boolean = false, problems: List<String> = emptyList())` e `REVIEW_SCHEMA: OutputSchema` (`parecer`); `class ReviewLoop(producer: Agent, reviewer: LlmClient, reviewerPrompt: String, tickets: TicketGateway, events: EventSink, maxRevisions: Int = 2) { suspend fun answer(question: String): SupportAnswer }`; `class AnswerCustomer(factory: (EventSink) -> ReviewLoop) { operator fun invoke(question: String): Flow<AgentEvent> }` (um `ReviewLoop` novo por pergunta; o último evento é `Completed`).

- [ ] **Step 1:** Testes portados do estudo: `aprova na terceira proposta (código reprova ticket inventado, revisor reprova incompleta, depois aprova)` e `o revisor só é chamado quando o código aprova`; `limite de 2 ajustes escala por código: ticket novo, confidence 0.0, escalated=true`; `escalação por limite reaproveita o ticket aberto do mesmo e-mail`; `reprovado sem motivo vira "revisor reprovou sem motivo"`; `emite ReviewRound por rodada e Escalated ao escalar`; `AnswerCustomer emite Completed por último e não mistura eventos de duas perguntas simultâneas`.
- [ ] **Step 2:** Rodar `./gradlew :core:jvmTest --tests "dsupport.core.review.*" --tests "dsupport.core.usecase.*"` → FAIL.
- [ ] **Step 3:** Implementar (checagens de código primeiro, revisor depois; `REVIEWER_PROMPT` neutro). Ligar o `AnswerCustomer` no `cli`.
- [ ] **Step 4:** Rodar os testes → PASS.
- [ ] **Step 5:** Commit único e tag `etapa-8.2`.

### Task 9.1: Eventos, trace e custo (etapa 9.1) [D]

**Files:**
- Create: `docs/stages/9-1-trace.md` (guia, Claude), `core/.../trace/{TraceReport,AgentCost}.kt`, `adapters/src/jvmMain/.../trace/TraceFiles.kt`
- Test: `core/src/commonTest/.../trace/TraceReportTest.kt`, `adapters/src/jvmTest/.../trace/TraceFilesTest.kt`

**Interfaces:**
- Consumes: `AgentEvent` (Tarefa 1).
- Produces: `data class AgentCost(agent: String, calls: Int, inputTokens: Int, outputTokens: Int, modelMs: Long)`; `object TraceReport { fun tree(events: List<AgentEvent>): String; fun byAgent(events: List<AgentEvent>): List<AgentCost>; fun slowest(events: List<AgentEvent>, n: Int = 3): List<AgentEvent> }`; `fun saveTrace(dir: Path, title: String, events: List<AgentEvent>): Path` (JSON, um arquivo por pergunta).

- [ ] **Step 1:** Claude escreve o guia: de onde vêm o custo e a árvore (só dos eventos); por que não há estado global; aninhamento por `depth`.
- [ ] **Step 2:** Testes (Diego): `a árvore aninha o evento do especialista sob a ferramenta que o chamou`; `byAgent soma tokens por agente (2 turnos de 1000 → 2000 do orquestrador; 300 do especialista)`; `slowest ignora o evento da pergunta inteira`; `saveTrace grava JSON legível com o nome do agente`.
- [ ] **Step 3:** Rodar `./gradlew :core:jvmTest --tests "dsupport.core.trace.*" :adapters:jvmTest --tests "dsupport.adapters.trace.*"` → FAIL.
- [ ] **Step 4:** Diego implementa; o `cli` imprime a árvore e o custo ao final de cada pergunta, a partir do `Flow` do `AnswerCustomer`.
- [ ] **Step 5:** Rodar os testes → PASS. Claude revisa.
- [ ] **Step 6:** Commit único e tag `etapa-9.1`.

### Task 9.2: Avaliação (etapa 9.2) [M + D]

**Files:**
- Create: `data/golden.json` (15 casos, do estudo, neutralizados), `eval/src/main/kotlin/dsupport/eval/{GoldenCase,CaseResult,Scoring,EvalReport,EvalRunner}.kt`, `reports/.gitkeep`
- Test: `eval/src/test/kotlin/dsupport/eval/{ScoringTest,EvalReportTest,GoldenDatasetTest,EvalRunnerFailureTest}.kt`

**Interfaces:**
- Consumes: `AnswerCustomer`, `SupportAnswerValidator`, `AgentEvent`, `TraceReport`.
- Produces: `@Serializable data class GoldenCase(id: String, question: String, expectedIntents: List<String>, shouldEscalate: Boolean?, mustContain: List<String>, expectedSources: List<String>)`; `data class CaseResult(...)` com os critérios separados e `error: String?`; `fun score(case: GoldenCase, answer: SupportAnswer, evidence: List<String>, events: List<AgentEvent>): CaseResult`; `fun failed(case: GoldenCase, error: String): CaseResult`; `class EvalReport(results: List<CaseResult>)` com `routing`, `grounded`, `facts`, `sources`, `escalationPrecision`, `escalationRecall`, `fun toMarkdown(title: String): String`; `main(args)` que roda os casos (ids opcionais) e grava `reports/eval-<data>.md`.

- [ ] **Step 1:** Testes portados do `EvalCheck`: `precisão 0.5 e recall 0.5 com 1 TP, 1 FP, 1 FN`; `ticket inventado não tem lastro`; `groundedness 0.8 em 5 casos`; `fontes: só casos que esperam fonte entram na métrica`; `latência, chamadas e tokens vêm dos eventos`; `golden.json carrega 15 casos e o ambíguo (g12) tem shouldEscalate = null`; `EvalRunner continua depois que um caso lança LlmApiException e marca esse caso como falha`.
- [ ] **Step 2:** Rodar `./gradlew :eval:test` → FAIL.
- [ ] **Step 3:** Claude implementa pontuação, relatório e golden; Diego escreve o `EvalRunner` (cada caso com tickets em arquivo temporário, montagem pelo mesmo `assemble` do `cli`).
- [ ] **Step 4:** Rodar `./gradlew :eval:test` → PASS (offline, sem chave).
- [ ] **Step 5:** Diego exporta `ANTHROPIC_API_KEY` e roda `./gradlew :eval:run`; conferir `reports/eval-<data>.md` com as 6 métricas e a tabela por caso.
- [ ] **Step 6:** Commit único (inclui o relatório) e tag `etapa-9.2`.

### Task 10: Fechamento da fase 1 [M]

**Files:**
- Modify: `README.md`
- Create: `docs/adr/0001-clean-architecture.md`, `0002-mcp-for-external-systems.md`, `0003-llm-client.md` (each with a `.pt-BR.md` twin) (se não existir), `docs/etapas/README.md`
- Create: `.env.example` (se faltar)

**Interfaces:**
- Consumes: relatório de `reports/` (Tarefa 9.2), diagrama de módulos da spec.

- [ ] **Step 1:** README: o que é, diagrama dos módulos e do loop, como rodar (`./gradlew test`, `:cli:run`, `:eval:run`), tabela com as métricas do último relatório e um trecho de trace real, tabela de etapas com as tags.
- [ ] **Step 2:** ADRs curtos (contexto, decisão, consequências) para as três decisões.
- [ ] **Step 3:** Verificar os critérios de sucesso da spec: `./gradlew clean test :core:compileKotlinWasmJs :contract:compileKotlinWasmJs` passa em JDK 21; critério 2 e 3 têm teste; relatório existe; todas as tags `etapa-0` … `etapa-9.2` existem (`git tag --list`).
- [ ] **Step 4:** Commit único e tag `fase-1`.
