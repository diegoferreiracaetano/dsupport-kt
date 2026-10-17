# Etapa 9.1 — Observability: eventos, tracing e custo por agente

[English](9-1-trace.md) | [Português](9-1-trace.pt-BR.md)

## Objetivo

Transformar o fluxo de `AgentEvent`s em algo que uma pessoa consegue ler: uma **árvore de rastro** mostrando quem chamou quem e quanto cada passo demorou, e uma **tabela de custo** com os tokens que cada agente gastou. Não há estado global: tudo é calculado a partir da lista de eventos de uma pergunta. Você escreve esta etapa.

| Peça | Antes | Etapa 9.1 |
|---|---|---|
| Eventos | lista plana; nome do agente e `depth` | **cada evento diz a que execução pertence (`runId`), e a execução de um subagente sabe quem é o pai (`parentRunId`)** |
| Árvore de rastro | nenhuma | **`TraceReport.tree`** |
| Custo por agente | nenhum | **`TraceReport.byAgent`** |
| Passos mais lentos | nenhum | **`TraceReport.slowest`** |
| Rastro em disco | nenhum | **`TraceFiles.save`** (um arquivo JSON por pergunta) |

## Conceito

**Observability** (observabilidade) é conseguir responder "o que aconteceu, em que ordem e quanto custou?" sem espalhar prints. A fonte única é o fluxo de eventos da etapa 1. Três ideias:

1. **Uma lista plana não basta quando os agentes rodam em paralelo.** O orquestrador pode iniciar dois especialistas `orders` no mesmo turno. Os eventos deles se intercalam e têm o mesmo nome de agente, então `agent` e `depth` não dizem qual `ToolFinished` pertence a qual execução. Cada execução de um agente precisa de um **id** (`runId`), e a execução de um subagente guarda o id de quem a iniciou (`parentRunId`). A árvore passa a ser um simples agrupamento por esses ids.
2. **Como o subagente descobre o pai?** Pelo **contexto da coroutine**. O `Agent.run` embrulha o trabalho num elemento `RunContext(runId, parentRunId)`. Um subagente é iniciado de dentro de uma chamada de ferramenta, que roda na coroutine do pai, então ele encontra o `RunContext` do pai em `coroutineContext` e registra o id. Nenhum parâmetro precisa passar por `Tool.run`. É a concorrência estruturada fazendo um trabalho útil, e um bom assunto de entrevista.
3. **Custo é uma soma sobre eventos.** Os tokens vêm do `ModelCalled`, que toda chamada de modelo emite: os turnos dos agentes, a resposta estruturada final e o revisor. Agrupar pelo nome do agente dá o custo por agente.

## O que escrever

**1. Contrato (`AgentEvent`).** Acrescente `runId: String = ROOT_RUN` como **último** parâmetro de `AgentStarted`, `ModelCalled`, `ToolStarted`, `ToolFinished` e `GuardActed`, e `parentRunId: String? = null` ao `AgentStarted`. Os valores padrão mantêm o código e os testes existentes compilando, e o `encodeDefaults` continua desligado, então rastros antigos ainda decodificam. Defina `const val ROOT_RUN = ""`. `ReviewRound`, `Escalated` e `Completed` não mudam.

**2. Ids de execução e contexto (novos, `core/agent`).**

```kotlin
fun interface RunIds { fun next(): String }
class SequentialRunIds : RunIds

class RunContext(val runId: String, val parentRunId: String?) : AbstractCoroutineContextElement(RunContext) {
    companion object Key : CoroutineContext.Key<RunContext>
}
```

`SequentialRunIds` devolve `r1`, `r2`, … e **precisa ser seguro para chamadas de coroutines concorrentes**. Confira qual contador atômico existe em `commonMain` para `jvm` e `wasmJs` (`kotlin.concurrent.atomics.AtomicInt` no Kotlin recente) antes de escolher; não use um `var` simples.

**3. `Agent` (alterar).** Acrescente o parâmetro de construtor `runIds: RunIds = SequentialRunIds()` (depois de `depth`). No `run`: pegue `val parent = coroutineContext[RunContext]?.runId`, crie o id da execução, guarde os dois em campos, embrulhe o trabalho em `withContext(RunContext(runId, parent))` e emita `AgentStarted(..., runId, parentRunId = parent)`. Todo evento que o agente emitir depois (`ModelCalled`, `ToolStarted`, `ToolFinished`, `GuardActed`, e os do `followUp` e do `finish`) leva esse `runId`. Na montagem, crie **um** `RunIds` por pergunta e compartilhe entre o orquestrador e todos os especialistas, para os ids serem únicos dentro da pergunta.

**4. `ReviewLoop` (alterar).** O revisor não é um `Agent`. O evento `ModelCalled` dele mantém o nome de agente `reviewer` e usa `runId = "reviewer"`. A árvore pendura na raiz os eventos cuja execução não tem `AgentStarted`.

**5. `TraceReport` e `AgentCost` (novos, `core/trace`).**

```kotlin
data class AgentCost(val agent: String, val calls: Int, val inputTokens: Int, val outputTokens: Int, val modelMs: Long)

class TraceReport {
    fun tree(events: List<AgentEvent>): String
    fun byAgent(events: List<AgentEvent>): List<AgentCost>
    fun slowest(events: List<AgentEvent>, n: Int = 3): List<AgentEvent>
}
```

- `tree`: agrupe os eventos por `runId`; a execução de um `AgentStarted` é um nó, e os filhos são as execuções cujo `parentRunId` é o id dele. Dentro de um nó, liste as chamadas de modelo e de ferramenta em ordem; uma chamada de ferramenta cujo nome é um subagente mostra o nó desse subagente aninhado embaixo. Indente por nível e imprima duração e tokens. Eventos de execuções desconhecidas ficam pendurados na raiz. O formato exato do texto é seu; deixe legível num terminal.
- `byAgent`: um `AgentCost` por nome de agente, a partir dos eventos `ModelCalled`, na ordem da primeira aparição.
- `slowest`: os `n` eventos com maior `durationMs` entre `ModelCalled` e `ToolFinished`, do mais lento para o mais rápido.
- Os três funcionam com lista vazia.

**6. `TraceFiles` (novo, `adapters/src/jvmMain/.../trace`).**

```kotlin
class TraceFiles(private val now: () -> String = { Clock.System.now().toString() }) {
    fun save(dir: Path, title: String, events: List<AgentEvent>): Path
}
```

Cria o diretório se precisar e grava `trace-<timestamp>.json` (troque o `:` do timestamp), com `{"title": ..., "events": [...]}`, formatado, usando o serializador do `AgentEvent`. Devolve o caminho.

**7. CLI.** Depois de cada pergunta, imprima a árvore e a tabela de custo (`byAgent`) e salve o rastro com o `TraceFiles`. O `traces/` já está no `.gitignore`.

## Testes (escreva primeiro)

Uma classe por classe de produção, MockK onde houver colaboradores, nomes `given … when … then should …`, sem comentários.

**`AgentTest`** (acrescentar): given an agent run when it emits events then should tag all of them with the same run id; given an agent started inside another agent's run when it emits `AgentStarted` then should record the parent run id; given a top-level agent when it starts then should have no parent run id; given two sub-agents running in parallel from one parent when they emit events then should give them distinct run ids and the same parent; given a `followUp` and a `finish` after a run when they emit then should keep the run id of the run.

**`SequentialRunIdsTest`**: os ids são `r1`, `r2`, …; given many concurrent callers then should never repeat an id.

**`TraceReportTest`**: given an orchestrator that calls one specialist when building the tree then should nest the specialist under the tool call that started it; given two specialists with the same name running in parallel when building the tree then should keep their events apart; given the reviewer call when building the tree then should attach it to the root; given 2 orchestrator turns of 1000 input tokens and 1 specialist turn of 300 when summing by agent then should return 2000 and 300; given a reviewer call when summing then should include the `reviewer` agent; given events with durations when asking for the slowest then should return the top `n` slowest model or tool events; given no events then all three return empty results.

**`TraceFilesTest`**: given events when saving then should write a readable JSON file named from the timestamp inside the directory; given a directory that does not exist then should create it; given the saved file when decoding it then should give back the same events.

**`AgentEventTest`** (contract): given an event without `runId` in the JSON when decoding then should default it (rastros antigos ainda carregam).

Atualize também os lugares que montam eventos à mão (`AgentTest`, `ReviewLoopTest`, `CaseScorerTest`): graças aos valores padrão eles devem continuar compilando.

## Rodar

```bash
./gradlew test
REFERENCE_DATE=2026-10-15 ./gradlew :cli:run --args="My order 1042 has not arrived. My email is ana@example.com"
```

## O que observar

- Na árvore impressa, os especialistas aninhados sob as chamadas de ferramenta do orquestrador, e as ações das guardas sob a chamada que afetaram.
- Na tabela de custo, qual agente gasta mais (em geral o orquestrador, que carrega a conversa toda) e quanto custa o revisor.
- No JSON salvo, os campos `runId` e `parentRunId` que reconstroem a árvore.

## Exercícios (opcionais)

- Acrescente um preço por milhão de tokens por modelo e imprima o custo em dólares ao lado dos tokens.
- Imprima a cadeia mais longa de execuções aninhadas (a profundidade da delegação).
- Compare o custo da mesma pergunta com o `ANTHROPIC_MODEL` em dois modelos diferentes.

## Como explicar na entrevista

"Cada passo emite um evento, e o rastro, a tabela de custo e as métricas da avaliação são só funções sobre essa lista, então não há estado global. Quando fiz os especialistas rodarem em paralelo, uma lista plana de eventos deixou de dizer qual resultado de ferramenta pertencia a qual execução, então dei um id a cada execução de agente e propaguei o pai pelo contexto da coroutine. A árvore do rastro vira um agrupamento por esses ids."
