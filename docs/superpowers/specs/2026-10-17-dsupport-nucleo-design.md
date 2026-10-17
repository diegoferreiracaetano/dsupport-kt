# DSupport — Fase 1: núcleo (design)

Data: 2026-10-17 · Autor: Diego · Status: aprovada para planejamento, com a decisão de usar Kotlin Multiplatform

## 1. Objetivo

Construir, do zero e em Kotlin Multiplatform, um assistente de suporte ao cliente multi-agente, com qualidade de portfólio:
Clean Architecture, ferramentas fáceis de adicionar, MCP pelo SDK oficial, LLM trocável, testes offline,
avaliação com números versionados. O projeto é **novo e separado** do estudo `DsupportKotlin`, que não é alterado.

O resultado final alimenta uma **apresentação didática, passo a passo** (agente, RAG, MCP, multi-agentes,
guardas, validação). Por isso o projeto é construído na ordem das etapas e cada etapa fecha com uma tag git.

**Domínio:** suporte de uma loja genérica (e-commerce). Nenhuma marca nos dados nem nos prompts.

## 2. Fora de escopo desta fase

- Servidor Ktor e app Compose Desktop (fase 2, spec própria, depois do núcleo).
- Habilitar os alvos Android, iOS e Web (Wasm): na fase 1 os módulos KMP declaram só o alvo `jvm`, mas o código
  compartilhado já é escrito para todos (ver 4.8). Os outros alvos entram na fase 2, junto com o app.
- OpenTelemetry, frameworks de agente (Koog etc.) e injeção de dependência por framework (construtores à mão).
- Um segundo provedor de LLM funcionando. A porta tem que permitir, mas só o Claude é implementado.

## 3. Decisões

| Tema | Decisão | Motivo |
|---|---|---|
| Linguagem/plataforma | Kotlin Multiplatform; JDK 21 (toolchain) para a JVM | Mesmo núcleo servindo servidor e clientes Android, iOS e Web; o JDK 26 do sistema não funciona com o Gradle 8.10 |
| Arquitetura | Clean Architecture em módulos Gradle | O build impede violar a regra de dependência |
| LLM | Porta `LlmClient` genérica, sem tipos de provedor no `core` | Trocar de provedor sem tocar nos agentes |
| MCP | SDK oficial `io.modelcontextprotocol:kotlin-sdk` (cliente e servidor), JVM | Uso real do ecossistema, sem protocolo à mão |
| Ferramentas | Porta `Tool` + provedores (local e MCP) + configuração | Adicionar ferramenta sem mexer no agente |
| Serialização | `kotlinx.serialization` | Funciona em qualquer alvo; o Gson é só JVM |
| Observabilidade | `Flow<AgentEvent>`, sem singletons | Trace, custo e tela ao vivo saem da mesma fonte |
| Concorrência | Coroutines; `LlmClient` é `suspend` | Especialistas independentes rodam em paralelo |
| Git | Um repositório; tag `etapa-N` por etapa | A apresentação mostra o código real de cada passo |

## 4. Arquitetura

### 4.1 Módulos e regra de dependência

```
dsupport-kt/
├── contract/      KMP · tipos compartilhados: SupportAnswer, AgentEvent, ChatRequest
├── core/          KMP · domínio + aplicação: Agent, orquestrador, guardas, revisor, PORTAS
├── adapters/      KMP · commonMain (LLM via Ktor client, RAG) + jvmMain (arquivos, cliente MCP stdio)
├── mcp-orders/    JVM · servidor MCP de pedidos (SDK oficial, processo separado)
├── mcp-tickets/   JVM · servidor MCP de tickets (idem)
├── testing/       KMP (jvm) · suíte de contrato do LlmClient, só para testes
├── cli/           JVM · ponto de entrada de linha de comando e a montagem (composition root)
├── eval/          JVM · golden dataset, runner e relatório
├── data/          kb/, orders.json, tickets.json, golden.json
├── config/        mcp-servers.json, agents (quais ferramentas cada agente enxerga)
├── docs/          adr/, stages/, presentation/, superpowers/specs/
└── .github/workflows/ci.yml
```

```
contract ◄── core ◄── adapters ◄── cli, eval, mcp-*
```

- `core` depende só de `contract` e de bibliotecas puras (coroutines, serialization). Não conhece HTTP, arquivo,
  SDK de provedor nem processo.
- `adapters` implementa as portas do `core`.
- `cli` e `eval` montam tudo (composition root). `mcp-*` são aplicações independentes que usam os repositórios
  de `adapters`.
- Na fase 2, `server` (Ktor, JVM) e `app` (Compose Multiplatform) entram como módulos novos; o `app` depende só
  de `contract` e fala com o servidor por HTTP. O servidor é obrigatório: a chave da API e os processos MCP ficam nele,
  nunca no celular nem no navegador.

### 4.2 Pacotes do `core`

| Pacote | Conteúdo |
|---|---|
| `port` | `LlmClient`, `Tool`, `ToolProvider`, `KnowledgeSearch`, `OrderGateway`, `TicketGateway`, `EventSink` |
| `agent` | `Agent` (o loop), `subAgentTool`, definição de agente, prompts |
| `guard` | `Hook`, `Decision` e as guardas |
| `review` | `ReviewLoop` com limite de revisões |
| `usecase` | `AnswerCustomer`: o ponto de entrada da aplicação |

### 4.3 LLM genérico

O `core` define o vocabulário neutro, sem nada de provedor:

```kotlin
interface LlmClient {
    suspend fun next(request: LlmRequest): LlmReply          // texto ou pedido de ferramentas
    suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject
}
```

- `LlmRequest` leva: instruções do sistema, histórico (`Message`) e as `ToolSpec` visíveis.
- `LlmReply` é `Text` ou `ToolCalls` e traz o uso de tokens (`Usage`) para o evento de custo.
- Cada adaptador traduz esse vocabulário para o formato do seu provedor. O primeiro é `AnthropicLlmClient`
  (Claude), escrito com o cliente HTTP do Ktor em `commonMain`. Se na etapa 2 optarmos pelo SDK oficial Java da
  Anthropic (só JVM), ele fica em `jvmMain`. Em qualquer caso a escolha é **do adaptador** e não afeta o `core`.
- Uma suíte de testes de contrato (`LlmClientContract`) roda contra qualquer implementação: um segundo provedor
  futuro só precisa passar nela.
- O loop agêntico continua escrito por nós. O SDK de qualquer provedor serve só de transporte e tipos,
  nunca de "runner" de ferramentas.

### 4.4 Ferramentas: fáceis de criar e organizar

```kotlin
interface Tool {
    val spec: ToolSpec                       // nome, descrição, schema de entrada, anotações
    suspend fun run(args: JsonObject): ToolResult
}
data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject,
                    val readOnly: Boolean = true, val destructive: Boolean = false)
data class ToolResult(val content: String, val isError: Boolean = false)

interface ToolProvider { suspend fun tools(): List<Tool> }
```

Duas implementações de `ToolProvider`:

- **`LocalToolProvider`**: ferramentas Kotlin dentro do processo (ex.: `search_kb`, `create_ticket`,
  os subagentes). Ficam em pacote próprio por funcionalidade, uma classe por ferramenta.
- **`McpToolProvider`**: lê `config/mcp-servers.json`, sobe cada servidor, descobre as ferramentas por
  `tools/list` e as expõe como `Tool` comuns. O agente não percebe a diferença.

**Receitas para adicionar uma ferramenta** (a organização existe para isso):

1. *Ferramenta local:* criar uma classe que implementa `Tool`, registrá-la no `LocalToolProvider`.
2. *Ferramenta via MCP:* acrescentar uma entrada em `config/mcp-servers.json`. Nenhuma linha de código no agente.
3. *Quem enxerga o quê:* `config/agents` lista, por agente, as ferramentas permitidas (lista de permissão).
   O orquestrador não recebe ferramenta de dados, só especialistas.

As guardas se aplicam a qualquer ferramenta, local ou MCP, pelo nome e pelas anotações
(`readOnly`, `destructive`). Ferramenta destrutiva sem guarda correspondente é rejeitada na montagem.

### 4.5 Tudo é via MCP? Não.

| Quando | Onde |
|---|---|
| Sistema externo, com dono próprio, reutilizável por outros clientes (pedidos, tickets, transportadora) | **MCP**, processo separado |
| Lógica interna do agente (busca no RAG, subagentes, saída tipada) | **Local**, no processo |

MCP custa um processo, serialização e latência. Para lógica interna isso é custo sem benefício; para
sistemas externos é exatamente o desacoplamento que se quer mostrar. A porta `Tool` esconde a diferença, então
mover uma ferramenta de local para MCP, ou o contrário, é uma mudança de configuração e de montagem, não de agente.

### 4.6 Eventos

O agente emite `Flow<AgentEvent>` (tipos em `contract`): modelo chamado (tokens, tempo), ferramenta iniciada e
terminada, guarda bloqueou/alterou, rodada de revisão, escalou, resposta final. Essa única fonte alimenta:
o relatório de custo por agente, a árvore do trace, as métricas da avaliação e (fase 2) a tela ao vivo.
Não existe estado global (`Usage` e `Tracer` do estudo deixam de existir).

### 4.7 Guardas (hooks determinísticos)

Mantidas do estudo, agora sobre `Tool`/`ToolSpec`: `IdentityGate` (e-mail + pedido), `Redaction` (CPF/cartão),
`TicketDedup`, `MentionTicket`, `RedactResults` (ligada de fato na montagem, ao contrário do estudo).
Os padrões de CPF, cartão e e-mail ficam num único lugar (`guard/Patterns.kt`).

### 4.8 Regras de Kotlin Multiplatform

- `contract` e `core` só têm `commonMain` e `commonTest`: nada de `java.*`, `File`, `ProcessBuilder` nem Gson.
  Tempo, arquivo e processo entram por portas, implementadas em `jvmMain` de `adapters`.
- Código que hoje usa API só da JVM (`Normalizer` no TF-IDF, leitura de `data/`, cliente MCP via stdio)
  vai para `jvmMain`, atrás de uma porta. Evitamos `expect/actual` quando uma porta resolve.
- Na fase 1, `contract` e `core` habilitam `jvm` e `wasmJs`; `testing`, `adapters` e as aplicações habilitam só `jvm`.
  Os testes rodam na JVM (rápidos, sem emulador) e usam MockK, que só existe na JVM: ficam em `jvmTest`. A garantia de que o código compartilhado é portátil vem de compilar o alvo
  Wasm (`compileKotlinWasmJs`) num job de CI desde a etapa 0. iOS e Android entram na fase 2, com o app.
- Projetos multiplataforma não têm `testFixtures`: a suíte `LlmClientContract` fica no módulo `testing`,
  usado apenas como dependência de teste.
- `mcp-orders`, `mcp-tickets`, `cli`, `eval` e `server` são aplicações JVM comuns que consomem os módulos KMP.

## 5. Etapas (uma tag por etapa)

Cada etapa já nasce nas camadas finais. **Quem escreve:** M = eu (esqueleto, migração, testes);
D = você escreve o que é novo, com um guia meu antes.

| Etapa | Entrega | Autor |
|---|---|---|
| 0 | Esqueleto Gradle (catálogo de versões, toolchain 21), módulos vazios, CI, README inicial | M |
| 1 | `Agent` (loop) + `Tool` + `LlmClient` com modelo falso de teste | M migra, D revisa |
| 2 | `AnthropicLlmClient` no `adapters`, com a suíte de contrato | D |
| 3 | Ferramentas reais, repositórios JSON, `LocalToolProvider` | M |
| 4 | RAG: chunker, embedder TF-IDF, embedder Voyage, busca com score | M migra |
| 5 | MCP: servidores `mcp-orders`/`mcp-tickets` e `McpToolProvider` com o SDK oficial | D |
| 6 | Multi-agentes: orquestrador + especialistas, execução paralela com coroutines | D |
| 7 | Guardas | M migra |
| 8.1 | Saída tipada (`SupportAnswer`) com validação em código | M |
| 8.2 | Revisor com limite de revisões | M |
| 9.1 | `AgentEvent` + coletor de trace e custo | D |
| 9.2 | Avaliação: golden dataset, runner, relatório em `reports/` | M + D |

Depois: fase 2 (etapas 10 e 11: Ktor e Compose Desktop) e etapa 12 (README final e apresentação).

## 6. Dados

Em `data/`: artigos da central de ajuda em Markdown (frete, prazos, trocas e devoluções, pagamentos,
garantia, conta, cancelamento), pedidos e clientes fictícios, tickets (começa vazio) e o golden dataset.
Reaproveitados do estudo e **limpos de qualquer marca**. O `tickets.json` volta a `[]` antes de cada commit.

## 7. Testes e qualidade

- `kotlin.test` + JUnit 5 em cada módulo. O `./gradlew test` roda tudo offline, sem chave.
- Cada teste é sobre uma classe de produção, com os colaboradores simulados por MockK (`coEvery`/`coVerify`).
  Nomes no formato `given <context> when <action> then should <result>`, em inglês e sem comentários.
  Para a fronteira HTTP do Ktor usa-se o `MockEngine`. Nenhum dublê em código de produção.
- Testes de contrato para `LlmClient` e `ToolProvider`.
- CI (GitHub Actions): testes a cada push. A avaliação com API roda sob demanda; o relatório fica versionado.
- Segredos só por variável de ambiente (`ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`); `.env.example` no repositório.

## 8. Critérios de sucesso

1. `./gradlew test` passa offline em máquina limpa com JDK 21.
2. Adicionar uma ferramenta MCP nova = editar `config/mcp-servers.json` (sem código no agente), demonstrado numa etapa.
3. Trocar o provedor de LLM = escrever um adaptador novo que passa em `LlmClientContract`; o `core` não muda.
4. O relatório de avaliação (roteamento, groundedness, precisão/recall de escalação, latência, tokens) está em `reports/`.
5. Cada etapa tem tag, e o README mostra o diagrama e os números.
6. `contract` e `core` compilam em pelo menos um alvo não-JVM (Wasm ou iOS), provando que são realmente portáveis.

## 9. A confirmar durante a execução

- Coordenadas e versões atuais do SDK de MCP (`io.modelcontextprotocol:kotlin-sdk`) e quais transportes o
  servidor JVM suporta; confirmar antes da etapa 5.
- Versão do Gradle compatível com JDK 21 e Kotlin atual; fixar na etapa 0.
- Quais alvos já dá para compilar no CI da etapa 0 (iOS exige macOS no runner; Wasm roda em Linux) e a maturidade
  atual do Compose Multiplatform em iOS e Web, antes da fase 2.
- Se o `AnthropicLlmClient` usa o SDK oficial Java (`com.anthropic:anthropic-java`) ou HTTP direto; decidir na etapa 2
  pelo custo de manter, sem impacto no `core`.
