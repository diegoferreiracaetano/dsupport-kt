# Etapa 2 — O Claude de verdade responde

## Objetivo

Trocar o modelo roteirizado de teste por um cliente que chama a API da Anthropic. O `Agent` e as ferramentas **não mudam**: só entra um adaptador novo atrás da porta `LlmClient`. Você escreve este adaptador.

| Peça | Etapa 1 | Etapa 2 |
|---|---|---|
| Loop agêntico (`Agent`) | real | real, sem mudar |
| `LlmClient` | `ScriptedLlmClient` (teste) | **`AnthropicLlmClient` (real)** |
| Ferramentas | só nos testes | só nos testes |

Local: módulo `adapters`, em `commonMain` (código multiplataforma, sem `java.*`).

## Conceito: o protocolo de tool use

A API não tem memória: **cada chamada manda o histórico inteiro** e a lista de ferramentas. O modelo nunca executa nada, ele só pede. Quem executa é o seu código, e o resultado volta como a próxima mensagem.

Como o nosso vocabulário neutro vira JSON da API:

| No Kotlin (`core`) | Na API | Observação |
|---|---|---|
| `Message.User(text)` | `{"role":"user","content":"texto"}` | |
| `Message.AssistantText(text)` | `{"role":"assistant","content":"texto"}` | |
| `Message.AssistantToolCalls(calls)` | `{"role":"assistant","content":[{"type":"tool_use","id":..,"name":..,"input":{..}}]}` | um bloco por chamada |
| `Message.ToolResult(...)` | bloco `{"type":"tool_result","tool_use_id":..,"content":"..","is_error":true?}` dentro de uma mensagem **`user`** | resultado de ferramenta volta como `user` |

Três regras que quebram a requisição se esquecidas (e que entrevistadores adoram):

1. **O `id` liga pedido e resultado.** O `tool_result` devolve o mesmo `id` do `tool_use`.
2. **Resultados do mesmo turno vão numa mensagem só.** Se o modelo pediu duas ferramentas, os dois `tool_result` ficam como blocos da **mesma** mensagem `user`. Separar em duas mensagens ensina o modelo a parar de pedir em paralelo.
3. **Papéis têm que alternar.** Duas mensagens `user` seguidas (por exemplo, o resultado de ferramenta e depois o texto "devolva a resposta final") viram **uma** mensagem, com os blocos `tool_result` primeiro e o texto depois.

### A requisição

`POST https://api.anthropic.com/v1/messages`, com os cabeçalhos `x-api-key`, `anthropic-version: 2023-06-01` e `content-type: application/json`.

```json
{
  "model": "claude-haiku-5-5",
  "max_tokens": 4096,
  "system": "Você é um assistente de suporte.",
  "thinking": { "type": "disabled" },
  "tools": [
    { "name": "get_order_status", "description": "...", "input_schema": { "type": "object", "properties": { "order_id": { "type": "string" } }, "required": ["order_id"] } }
  ],
  "messages": [
    { "role": "user", "content": "Cadê meu pedido 1042?" }
  ]
}
```

### A resposta

```json
{
  "content": [
    { "type": "text", "text": "Vou consultar." },
    { "type": "tool_use", "id": "toolu_01A", "name": "get_order_status", "input": { "order_id": "1042" } }
  ],
  "stop_reason": "tool_use",
  "usage": { "input_tokens": 312, "output_tokens": 54 }
}
```

Leia os blocos **por `type`**, nunca pela posição. Se houver `tool_use`, devolva `LlmReply.ToolCalls` com todos os blocos `tool_use`; senão, junte os blocos `text` em `LlmReply.Text`. O uso de tokens sai de `usage`.

## Três decisões de desenho (já tomadas, você as registra no ADR)

**1. HTTP direto com Ktor, em `commonMain`, em vez do SDK oficial.** O SDK oficial da Anthropic para Kotlin é o de Java, que só roda na JVM. Como o `adapters` tem código multiplataforma e o objetivo da etapa é entender o protocolo, escrevemos o cliente sobre o `HttpClient` do Ktor. O custo é manter o mapeamento JSON; o benefício é portabilidade e transparência. Se um dia quiser o SDK, ele entra em `jvmMain` atrás da mesma porta, e o `core` nem percebe.

**2. `thinking: {"type":"disabled"}` sempre.** No Claude Haiku 5.5 o raciocínio vem **ligado por padrão**: a resposta pode começar com blocos `thinking`, e esses blocos teriam que voltar ao histórico sem alteração. Nosso `Message` neutro não guarda blocos de raciocínio de um provedor específico, então desligamos o raciocínio (aceito no Haiku 5.5 com o `effort` padrão). Consequência para registrar no ADR: modelos que **não** aceitam `disabled` (como o Claude Opus 5.5) dão erro 400 com este cliente. Se a resposta trouxer um bloco `thinking` mesmo assim, lance `LlmApiException` com uma mensagem clara em vez de ignorá-lo em silêncio.

**3. Saída estruturada por `output_config.format`, não por `tool_choice` forçado.** No estudo, o formato final virava uma "ferramenta" forçada. Isso hoje dá erro 400 em vários modelos (Claude Opus 5.5, Sonnet 5.5, Fable 5.1). O caminho que vale para todos é:

```json
"output_config": {
  "format": { "type": "json_schema", "schema": { "type": "object", "properties": { "...": {} }, "required": ["..."], "additionalProperties": false } }
}
```

A resposta vem como **texto JSON** num bloco `text`; você faz o parse para `JsonObject`. Limites do esquema: todo objeto precisa de `additionalProperties: false`, e não há `minimum`/`maximum`/`minLength` (por isso a validação de faixa, como `confidence` entre 0 e 1, continua no nosso código). Mantenha `tools` declaradas na requisição estruturada, porque o histórico pode conter chamadas a elas.

## O que escrever

Arquivos novos em `adapters/src/commonMain/kotlin/dsupport/adapters/llm/`:

```kotlin
class LlmApiException(val status: Int?, message: String) : Exception(message)

class AnthropicLlmClient(
    private val apiKey: String,
    private val modelId: String = "claude-haiku-5-5",
    private val http: HttpClient,
    private val maxTokens: Int = 4096,
    private val maxRetries: Int = 2,
) : LlmClient {
    override suspend fun next(request: LlmRequest): LlmReply
    override suspend fun structured(request: LlmRequest, schema: OutputSchema): JsonObject
}
```

O mapeamento fica separado, em `AnthropicMapping.kt`, para testar sem rede:

```kotlin
internal fun buildRequestBody(modelId: String, maxTokens: Int, request: LlmRequest, schema: OutputSchema? = null): JsonObject
internal fun parseReply(body: JsonObject): LlmReply
internal fun parseStructured(body: JsonObject): JsonObject
```

Regras de comportamento:

- Omita `tools` quando a lista for vazia. Não envie `temperature`, `top_p` nem `top_k` (o Haiku 5.5 rejeita valores fora do padrão). Não use `tool_choice`.
- `ToolSpec.inputSchema` já é o JSON Schema: vai direto em `input_schema`. `isError = true` vira `"is_error": true` no `tool_result`.
- **Repetições:** status 429 e qualquer 5xx (inclui 529, "sobrecarregado") repetem até `maxRetries` vezes, com espera crescente (`delay`, por exemplo 500 ms e depois 1000 ms). 400, 401, 403 e 404 **não** repetem: lançam `LlmApiException` na hora, com o status e o corpo da resposta.
- Corpo sem `content`, `stop_reason` igual a `refusal`, ou `max_tokens` numa chamada estruturada: `LlmApiException` com mensagem clara.
- Dependências: `adapters` já tem `ktor-client-core` em `commonMain` e o `ktor-client-cio` em `jvmMain`. A chamada usa `http.post(url) { header(...); contentType(ContentType.Application.Json); setBody(json.toString()) }` e `response.bodyAsText()`. Não precisa do plugin de content negotiation: você monta o JSON com `buildJsonObject`, igual ao estudo.

## Testes (escreva primeiro)

Em `adapters/src/jvmTest/kotlin/dsupport/adapters/llm/`, com o `MockEngine` do Ktor (nada de rede nem chave). Nomes de teste, mensagens e KDoc em inglês, sem comentários:

```kotlin
private fun client(engine: MockEngine) = AnthropicLlmClient(
    apiKey = "test-key", http = HttpClient(engine), maxRetries = 2,
)
```

`AnthropicMappingTest` e `AnthropicLlmClientTest`:

- `maps system, messages and tools to the API JSON`
- `tool result becomes a tool_result with tool_use_id inside a user message`
- `several results of the same turn go in a single message`
- `consecutive user messages become one, with tool_result before the text`
- `is_error is set on the tool_result when the tool failed`
- `omits tools when there are none and never sends tool_choice`
- `reads several tool_use blocks as ToolCalls and the usage tokens`
- `structured sends output_config.format with the schema and reads the JSON from the text`
- `429 is retried and the later good response is returned` (the `MockEngine` answers 429 and then 200)
- `500 is retried up to maxRetries and then throws LlmApiException`
- `401 throws immediately without retrying` (count the engine calls: exactly one)
- `body without content throws LlmApiException`
- `thinking block throws LlmApiException`

E a suíte de contrato, que prova que o cliente cumpre a mesma porta que o modelo roteirizado:

```kotlin
class AnthropicLlmClientContractTest : LlmClientContract() {
    override fun clientFor(scenario: Scenario): LlmClient =
        AnthropicLlmClient(apiKey = "test-key", http = HttpClient(MockEngine { respond(jsonFor(scenario), HttpStatusCode.OK, jsonHeaders) }))
}
```

Aqui `jsonFor(scenario)` devolve o corpo de resposta da API para cada cenário: `Scenario.Text` vira um bloco `text`; `Scenario.ToolCall` vira um bloco `tool_use` com o mesmo `id`, nome e argumentos; `Scenario.Structured` vira um bloco `text` cujo conteúdo é o JSON do objeto.

## Rodar

```bash
./gradlew :adapters:jvmTest
./gradlew test :adapters:compileKotlinJvm
```

Esperado: todos os testes passam, sem chave e sem rede. Só depois, e só se você quiser ver o Claude de verdade, exporte `ANTHROPIC_API_KEY` e faça uma chamada manual (a montagem completa vem na etapa 6).

## O que observar

- O JSON que o seu `buildRequestBody` gera para uma conversa com ferramenta: confira que o `id` aparece nas duas pontas.
- Quantas mensagens saem para a API quando o histórico termina em `ToolResult` seguido de um `User`: deve ser **uma**.
- O número de chamadas que o `MockEngine` recebe no teste do 401 (uma) e no do 500 (três: a original e duas repetições).

## Exercícios (opcionais, independentes)

- Faça o cliente respeitar o cabeçalho `retry-after` quando o 429 trouxer um.
- Acrescente o número de tokens em cache (`cache_read_input_tokens`) ao `Usage`.
- Escreva um segundo cliente (qualquer provedor, ou um falso) que passe em `LlmClientContract`. É o teste de que o `core` é de fato genérico.

## ADR (registre a decisão)

Crie `docs/adr/0003-cliente-llm.md` com contexto, decisão e consequências das três decisões acima: HTTP direto em `commonMain` em vez do SDK oficial (que é só JVM), `thinking` desligado por não carregarmos blocos de raciocínio no histórico neutro, e saída estruturada por `output_config.format`.

## Como explicar na entrevista

"A API do LLM é sem estado: eu mando o histórico e as ferramentas a cada chamada. O modelo só pede uma ferramenta, o meu código executa e devolve o resultado com o mesmo id, numa mensagem de usuário. Escondi tudo isso atrás de uma porta `LlmClient`, então trocar de provedor é escrever um adaptador novo que passe na suíte de contrato. E decidi consciente que a saída estruturada usa o formato JSON nativo da API, porque forçar uma ferramenta já não funciona em todos os modelos."
