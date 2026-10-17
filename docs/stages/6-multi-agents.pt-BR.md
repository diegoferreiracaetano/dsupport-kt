# Etapa 6 — Multi-agent orchestration (orquestração multiagente)

[English](6-multi-agents.md) | [Português](6-multi-agents.pt-BR.md)

## Objetivo

Transformar o agente único numa **equipe**: um orquestrador que nunca toca em dados e três especialistas (conhecimento, pedidos, escalação), cada um com o seu prompt e **só as ferramentas do seu trabalho**. O orquestrador delega chamando um especialista como se fosse uma ferramenta. Na mesma etapa o loop aprende a rodar as chamadas de ferramenta de um turno **em paralelo**, e o sistema inteiro é montado pela primeira vez, com as guardas da etapa 7, o ciclo de revisão da etapa 8.2 e uma execução de ponta a ponta de verdade contra a API. Você escreve esta etapa.

| Peça | Antes | Etapa 6 |
|---|---|---|
| Loop do `Agent` | as chamadas de um turno rodam uma depois da outra | **rodam em paralelo, com os resultados na ordem** |
| Especialistas | não existem | **`knowledge`, `orders`, `escalation`** |
| Orquestrador | não existe | **delega, nunca lê dados** |
| Ferramentas por agente | fixas nos testes | **lista de permissão em `config/agents.json`** |
| Montagem | nenhuma | **o `cli` monta guardas, agentes e ciclo de revisão** |

## Conceito

Um **subagente é uma ferramenta cuja implementação é outro agente**. Para o orquestrador, delegar é só uma chamada de ferramenta; para o especialista, a tarefa chega como a primeira mensagem de usuário. Três consequências que você precisa desenhar:

1. **Os especialistas não veem a conversa.** O orquestrador precisa escrever cada tarefa completa (número do pedido, e-mail, o que o cliente quer). O prompt diz isso.
2. **Cada chamada a um especialista precisa de um `Agent` novo.** Um `Agent` guarda o histórico num campo. Se o orquestrador consulta o especialista `orders` sobre dois pedidos no mesmo turno e as duas chamadas dividem uma instância, as duas execuções se corrompem. Por isso o `subAgentTool` recebe uma **fábrica**, não uma instância.
3. **Privilégio mínimo.** O orquestrador não tem nenhuma ferramenta de dados; cada especialista recebe só as ferramentas listadas para ele na configuração. A lista de permissão é o ponto: um prompt pode ser contornado, uma ferramenta que não existe não.

Chamadas paralelas: quando uma resposta do modelo traz várias chamadas de ferramenta, elas são independentes por construção, então rode-as ao mesmo tempo e devolva **todos os resultados juntos, na ordem original**. Com `kotlinx.coroutines` isso é `coroutineScope { calls.map { async { ... } }.awaitAll() }`.

## O que escrever

**1. `Agent` (alterar).** Rodar as chamadas de um turno em paralelo. Regras:

- O histórico recebe primeiro o `AssistantToolCalls` e depois as mensagens `ToolResult`, **na ordem das chamadas**, qualquer que seja a ordem em que terminaram.
- A falha de uma ferramenta não pode cancelar as irmãs: ela já vira um resultado de erro. Faça o mesmo para uma exceção lançada por uma **guarda**: vira resultado de erro só daquela chamada. `CancellationException` é sempre relançada.
- Toda chamada continua passando pelas guardas (`guarded(...)`), nunca direto para `tool.run`.

**2. `subAgentTool` (novo, `core/agent`).**

```kotlin
fun subAgentTool(name: String, description: String, newAgent: () -> Agent): Tool
```

Esquema de entrada: uma string, `task`. O `run` cria um agente com `newAgent()`, executa com a tarefa e devolve o texto dele como resultado. Uma exceção dentro do especialista vira resultado de erro. O agente especialista é criado com `depth = 1`, para o rastro mostrar o aninhamento.

**3. Modelo de configuração e catálogo (novos, `core`).**

```kotlin
@Serializable data class AgentDef(val tools: List<String>, val maxTurns: Int = 4)
@Serializable data class AgentsConfig(val agents: Map<String, AgentDef>)

class ToolCatalog(private val providers: List<ToolProvider>) {
    suspend fun select(names: List<String>): List<Tool>
}
```

O `select` devolve as ferramentas na ordem de `names`. Um nome desconhecido lança `IllegalArgumentException` citando o nome. Dois provedores com a mesma ferramenta lançam `IllegalStateException`. Os provedores são consultados uma vez só.

`config/agents.json`:

```json
{
  "agents": {
    "knowledge":  { "tools": ["search_kb"] },
    "orders":     { "tools": ["get_order_status", "track_shipment"] },
    "escalation": { "tools": ["create_ticket"] }
  }
}
```

**4. Prompts (novos, `core/agent/Prompts.kt`, todos em inglês).** Constantes `ORCHESTRATOR_PROMPT`, `KNOWLEDGE_PROMPT`, `ORDERS_PROMPT`, `ESCALATION_PROMPT` e `REVIEWER_PROMPT`. Rascunhos para começar (adapte à vontade). Os textos ficam em inglês, como o resto do código:

- Orquestrador: "You coordinate customer support. You never look up data yourself: always delegate to a specialist. `knowledge` answers policy questions. `orders` answers about status, delays and tracking. `escalation` opens a ticket for a human agent. Specialists cannot see the conversation, so write each task complete, with every detail they need. If the order number or the email is missing for an order question, ask the customer before delegating. If `orders` reports a delay, call `escalation` and tell the customer the ticket number. Write the final answer short, friendly and in English."
- Conhecimento: "You answer policy questions. Use `search_kb` and answer only from what it returns, naming the article titles you used. If the search finds nothing relevant, reply exactly `NO_ANSWER`."
- Pedidos: "You look up the order given in the task using your tools and report only facts: status, delivery estimate, whether it is late and by how many days, and tracking if asked. If the order is not found, say so."
- Escalação: "You open tickets for human agents. Use `create_ticket` with a short objective summary, priority `high` for delays or unhappy customers (otherwise `medium`) and the customer email. Reply only with the ticket number."
- Revisor: "You review support answers before they reach the customer. You do not talk to the customer. You receive the customer message, the specialist results and the proposed answer. Reject it if it states something the results do not support, leaves part of the question unanswered, promises something no policy or ticket backs, or is unfriendly or too long for a chat. Each problem must say what to fix. If everything is right, approve with an empty list."

**5. Montagem (nova, `cli`).** Uma classe que constrói tudo a partir das dependências:

```kotlin
class DSupportAssembly(
    llm: LlmClient,
    toolProviders: List<ToolProvider>,
    agents: AgentsConfig,
    tickets: TicketGateway,
    maxRevisions: Int = 2,
) {
    suspend fun answerCustomer(): AnswerCustomer
}
```

- `answerCustomer()` seleciona as ferramentas de cada especialista pelo `ToolCatalog`, monta as **guardas** `[Redaction, IdentityGate, RedactResults, MentionTicket, TicketDedup]` (`TicketDedup` **por último**, porque segura uma reserva) e chama `GuardPolicy().validate(todasAsFerramentasSelecionadas, guardas)` uma vez.
- A fábrica do `AnswerCustomer` recebe o `EventSink` de cada pergunta e constrói, **por pergunta**, as três fábricas de especialista (cada agente novo recebe `hooks = guardas`, `events = sink`, `depth = 1`), o orquestrador (`tools` = só os `subAgentTool`, `maxTurns = 8`, as mesmas guardas) e o `ReviewLoop`.
- O orquestrador não pode ter ferramenta de dados. Se a configuração der uma a ele, falhe na montagem.

**6. Ponto de entrada (`cli/Main.kt`).** Leia a pergunta dos argumentos. Ambiente: `ANTHROPIC_API_KEY` (obrigatória), `ANTHROPIC_MODEL` (padrão `claude-haiku-5-5`), `DATA_DIR` (padrão `data`). Monte o `HttpClient` (CIO), o `AnthropicLlmClient`, a base de conhecimento (`KbLoader` + `VectorKnowledgeBase` com `TfIdfEmbedder`), os repositórios JSON, os provedores e a montagem. Colete o `Flow<AgentEvent>`, imprima uma linha por evento (agente, o que fez, tokens) e o `SupportAnswer` final em JSON formatado.

- Provedores: `LocalToolProvider(listOf(SearchKbTool(kb), CreateTicketTool(tickets)))` para conhecimento e escalação. Para pedidos use `LocalToolProvider(GetOrderStatusTool, TrackShipmentTool)` até a etapa 5 terminar; depois dela, troque só essa linha por `McpToolProvider(config/mcp-servers.json)` e mais nada muda.
- **Data de referência:** "atrasado" depende de hoje. Aceite uma variável opcional `REFERENCE_DATE` (data ISO) e passe-a como `today` do `GetOrderStatusTool`, inclusive dentro do servidor `mcp-orders`. A avaliação vai fixá-la em `2026-10-15` para os resultados nunca derivarem.

## Testes (escreva primeiro)

Uma classe por classe de produção, MockK nos colaboradores, nomes `given … when … then should …`, sem comentários.

**`AgentTest`** (acrescentar à classe existente):

- given two tool calls in one turn when running then should run them in parallel (`runTest`, cada ferramenta simulada com `delay(1000)`, confira `testScheduler.currentTime == 1000`, e não 2000)
- given two tool calls where the second finishes first when running then should send the results back in the order of the calls
- given two tool calls where one throws when running then should still run the other and report the failure as an error result
- given a hook that throws for one call when running then should turn it into an error result for that call only
- given several tool calls when running then should consult the hooks once per call

**`SubAgentToolTest`**: given a task when running then should create a new agent, run it with that task and return its answer; given two simultaneous calls then should create two distinct agents; given a specialist that throws then should return an error result; given the tool when reading its spec then should require a `task` string.

**`ToolCatalogTest`**: seleção por nomes na ordem; nome desconhecido → `IllegalArgumentException` citando o nome; dois provedores com a mesma ferramenta → `IllegalStateException`; provedores consultados só uma vez.

**`AgentsConfigTest`**: given the project `config/agents.json` when decoding then should list the three specialists with their tools.

**`DSupportAssemblyTest`** (`cli/src/test`): given the standard configuration when assembling then should give the orchestrator only sub-agent tools; given a configuration that gives the orchestrator a data tool then should fail; given no guard covering `create_ticket` then should fail with the tool name.

**`EscalationAgentTest`** (`cli/src/test`, `JsonTicketGateway` real num arquivo temporário, `CreateTicketTool`, `TicketDedup` e `Redaction` reais, `LlmClient` simulado): given a reply with two `create_ticket` calls for the same customer when the agent runs then should create exactly one ticket and block the other. É a prova de ponta a ponta do requisito dos tickets paralelos.

## Rodar

```bash
./gradlew test
export ANTHROPIC_API_KEY=...        # a sua chave, nunca num arquivo que vá para o git
REFERENCE_DATE=2026-10-15 ./gradlew :cli:run --args="My order 1042 has not arrived. My email is ana@example.com"
```

Esperado no pedido atrasado: o orquestrador pede o status ao `orders`, vê 10 dias de atraso, pede um ticket ao `escalation`, a resposta cita o ticket (`TCK-...`) e o `data/tickets.json` ganha uma entrada (**volte para `[]` antes de commitar**). Teste também uma pergunta de política (só o `knowledge` é chamado) e uma pergunta sem e-mail (o orquestrador pergunta ao cliente, nenhuma ferramenta de dados é chamada).

## O que observar

- Os eventos em ordem: quem chamou quem, o que rodou em paralelo, qual guarda agiu (`GuardActed`), quantas rodadas de revisão.
- Os tokens por agente: o orquestrador é o mais caro, porque carrega a conversa inteira.
- Que o orquestrador nunca chama ferramenta de dados, e que um especialista não consegue chamar uma ferramenta fora da lista dele.

## Exercícios (opcionais)

- Dê ao especialista `knowledge` um `maxTurns` menor e veja o que um loop curto demais faz com a resposta.
- Troque o `ANTHROPIC_MODEL` por outro modelo e compare custo e qualidade da mesma pergunta.
- Acrescente um quarto especialista editando só o `config/agents.json` e a montagem.

## Como explicar na entrevista

"Um subagente é só uma ferramenta cuja implementação é outro agente, então o orquestrador não precisa de mecanismo especial para delegar. Dou a cada especialista só as ferramentas do trabalho dele por uma lista de permissão, porque uma ferramenta ausente é uma garantia mais forte que um prompt. As chamadas de um turno rodam em paralelo e voltam em ordem, e cada chamada recebe um agente novo para que execuções simultâneas não dividam histórico."
