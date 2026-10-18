# Etapas 11 e 12 — Um servidor e um app para interações reais

[English](11-12-interface.md) | [Português](11-12-interface.pt-BR.md)

## Objetivo

Deixar uma pessoa conversar com o assistente. Um **servidor Ktor** expõe o caso de uso `AnswerCustomer` por HTTP e um **app Compose Desktop** mostra a conversa, as fontes citadas, o selo de escalado e a árvore de rastro ao vivo.

## Como funciona

```text
app (Compose)  --POST /chat {"question": "..."}-->  servidor (Ktor)  -->  AnswerCustomer  -->  modelo
      ^                                                   |
      +------ text/event-stream: um AgentEvent por quadro ---+
```

- **Server-Sent Events.** O servidor escreve `data: <JSON do AgentEvent>` para cada evento e dá flush, então o app desenha o rastro enquanto o assistente ainda trabalha. Uma falha no meio do fluxo vira um quadro `event: error`. `GET /health` responde `ok`.
- **A chave fica no servidor.** Só o servidor lê `ANTHROPIC_API_KEY`; o app só conhece `DSUPPORT_SERVER` (padrão `http://localhost:8080`).
- **Um protocolo, dois lados.** `ChatRequest`, os quadros e o `SseParser` vivem em `contract`, então servidor e app não se afastam, e é testado uma vez.
- **O app tem camadas como o core.** `Assistant` é a porta, `ChatClient` o adapter, `ChatViewModel` o estado, `ChatScreen` o desenho. Tudo menos o desenho é testado com dublês; o desenho é conferido renderizando em PNG, sem janela.

## Rodar

```bash
set -a; source .env; set +a
./gradlew :server:run
```

Em outro terminal:

```bash
./gradlew :app:run
```

Opcional: `MCP_SERVERS_FILE=config/mcp-servers.json` (depois de `./gradlew installDist`) faz o servidor usar as ferramentas MCP. `PORT` muda a porta do servidor.

## Testes

- `ServerSentEventsTest` (`contract`): montar e ler quadros, incluindo o de erro.
- `ChatModuleTest` (`server`): streaming, validação (vazia, longa demais, não é JSON), falha no meio do fluxo, health.
- `ChatClientTest` (`app`, `MockEngine`): eventos em ordem, formato do pedido, quadro de erro, pergunta rejeitada, servidor inacessível.
- `ChatViewModelTest` (`app`): mensagens, rastro ao vivo, conclusão, falhas, perguntas vazias e simultâneas.
- `ChatScreenRenderTest` (`app`): renderiza a tela pronta e a tela em andamento em `app/build/render/`.

Nenhum teste chama a API do modelo.

## Próximos alvos

`contract` e `core` já compilam para JVM e Wasm. Clientes Android, iOS e Web podem reaproveitar o protocolo e o view model quando o app virar um módulo multiplataforma; isso fica como evolução.
