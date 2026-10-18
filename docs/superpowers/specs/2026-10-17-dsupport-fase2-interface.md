# DSupport — Fase 2: servidor e app (spec)

## Objetivo
Permitir interações reais com o assistente: um servidor Ktor expõe o `AnswerCustomer` e um app Compose Desktop deixa uma pessoa digitar perguntas e ver a resposta, as fontes, o selo de escalonamento e a árvore de rastro ao vivo.

## Decisões
- `POST /chat` recebe `{"question": "..."}` e responde `text/event-stream`: um quadro `data: <AgentEvent JSON>` por evento; erro no meio do fluxo vira `event: error`. `GET /health` responde `ok`.
- A chave da API existe só no servidor (`ANTHROPIC_API_KEY`); o app só conhece a URL (`DSUPPORT_SERVER`, padrão `http://localhost:8080`).
- O protocolo (`ChatRequest`, quadros SSE) mora em `contract`, compartilhado pelos dois lados e testado uma vez.
- O servidor reaproveita `DSupportRuntime` do módulo `cli`. Ferramentas MCP seguem opcionais (`MCP_SERVERS_FILE`).
- O app é Compose Multiplatform com alvo Desktop (JVM) primeiro; Android, iOS e Web ficam como evolução, `contract` e `core` já compilam para Wasm.
- A lógica do app (cliente, estado do chat) é testável sem UI; a tela é verificada renderizando de verdade.
- Nenhum teste chama a API: servidor com `testApplication` e `AnswerCustomer` dublê; cliente com `MockEngine`.

## Fora do escopo
Autenticação, persistência de conversas e histórico entre perguntas (cada pergunta é independente, como no núcleo).

## Plano
1. `contract`: `ChatRequest`, `ServerSentEvents` (quadros + `SseParser`), testes.
2. `server`: `chatModule`, `Main`, testes de contrato HTTP.
3. `app`: `ChatClient`, `ChatState`, telas, testes; render de verificação.
4. Docs bilíngues (guia, README, CHECKLIST), tags `etapa-11` e `etapa-12`.
