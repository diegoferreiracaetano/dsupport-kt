# DSupport — Checklist

[English](CHECKLIST.md) | [Português](CHECKLIST.pt-BR.md)

> Os dois arquivos andam juntos: toda alteração vale para as duas línguas.

Projeto novo e separado do estudo `DsupportKotlin`. Kotlin Multiplatform, Clean Architecture, MCP com o SDK oficial, LLM genérico.

- Spec: `docs/superpowers/specs/2026-10-17-dsupport-nucleo-design.md`
- Plano da fase 1: `docs/superpowers/plans/2026-10-17-dsupport-nucleo.md`

**Como usar:** um item por vez, na ordem. Eu explico antes, você confirma depois de testar. Cada etapa fecha com um commit e a tag `etapa-N`.
**Testes:** uma classe por teste, MockK, nomes `given … when … then should …`, em `jvmTest`.
**Código:** sem comentários (só KDoc quando for documentação) e tudo em inglês: nomes, mensagens, KDoc e nomes de teste.
**Autor:** **[M]** Claude faz · **[D]** Diego escreve, com guia prévio em `docs/stages/`.

---

## Antes de começar

- [ ] Revisar a spec e o plano (este checklist, a spec e o plano batem entre si)
- [ ] Escolher o método de execução (nativo ou subagent-driven; recomendado: nativo)
- [ ] `ANTHROPIC_API_KEY` disponível para as etapas 6 e 9.2 (você exporta; eu não digito chaves)
- [ ] `VOYAGE_API_KEY` só se quiser o embedding semântico (opcional; o TF-IDF funciona sem chave)

## Fase 1 — Núcleo

### E0 — Esqueleto [M] · tag `etapa-0`
- [x] Versões resolvidas: Kotlin 2.4.21, Gradle 9.8.1, coroutines 1.11.0, serialization 1.11.0, datetime 0.8.0, Ktor 3.6.0, SDK de MCP 0.15.0
- [x] Módulos `contract`, `core`, `testing`, `adapters`, `mcp-orders`, `mcp-tickets`, `cli`, `eval`
- [x] JDK 21 fixado no projeto (o JDK 26 do sistema não atrapalha)
- [x] `./gradlew test` verde (agregador na raiz roda o `jvmTest` dos módulos KMP) + compilação Wasm de `contract` e `core` (validada com código descartável; passa a valer de verdade na E1)
- [x] CI no GitHub Actions, `.gitignore`, `.env.example`, README inicial
- [x] `git init` e primeiro commit

### E1 — Vocabulário e loop do agente [M] · tag `etapa-1`
- [x] `AgentEvent` no `contract`
- [x] Portas `LlmClient`, `Tool`, `ToolProvider`, `EventSink`
- [x] `Agent` com loop, limite de turnos e ferramenta inexistente tratada
- [x] `LlmClientContract` no módulo `testing`; testes do `Agent` com MockK

### E2 — Cliente do LLM [D] · tag `etapa-2`
- [x] Guia `docs/stages/2-llm-client.pt-BR.md`
- [x] `AnthropicLlmClient` (mapeamento de ida e volta, `tool_use`/`tool_result`, saída estruturada)
- [x] Erros da API: 2 novas tentativas e depois erro claro
- [x] Passa na suíte `LlmClientContract`
- [x] ADR do cliente (SDK oficial ou HTTP direto)

### E3 — Ferramentas reais [M] · tag `etapa-3`
- [x] Domínio `Order`/`Ticket` e portas `OrderGateway`/`TicketGateway`
- [x] `get_order_status` (com atraso calculado), `track_shipment`, `create_ticket`
- [x] Repositórios JSON em `jvmMain`; dados sem marca
- [x] `LocalToolProvider`

### E4 — RAG [M] · tag `etapa-4`
- [x] Chunker por seção, normalização de acentos sem `java.text`
- [x] `TfIdfEmbedder` e `VoyageEmbedder`
- [x] `VectorKnowledgeBase` e `SearchKbTool` com corte de relevância
- [x] Artigos da central de ajuda em `data/kb/`

### E5 — MCP com o SDK oficial [D] · tag `etapa-5`
- [x] Guia `docs/stages/5-mcp.pt-BR.md` (versão e transporte confirmados nos fontes do SDK)
- [ ] `McpToolServer` e os servidores `mcp-orders` e `mcp-tickets`
- [ ] `McpConnector` (stdio) e `McpToolProvider` lendo `config/mcp-servers.json`, com tempo limite por chamada
- [ ] Servidor que cai ou não sobe devolve erro sem derrubar o agente
- [ ] Prova do critério: adicionar o servidor de tickets só editando a configuração

### E6 — Multi-agentes e paralelismo [D, feita pela Claude a pedido do Diego] · tag `etapa-6`
- [x] Guia `docs/stages/6-multi-agents.pt-BR.md`
- [x] `subAgentTool` (um agente novo por chamada), `ToolCatalog`, `config/agents.json` (lista de permissão por agente)
- [x] Turnos com várias chamadas rodam em paralelo, com ordem preservada e falhas isoladas
- [x] Prompts neutros em inglês; orquestrador sem ferramenta de dados
- [x] `DSupportAssembly`: guardas na ordem (`TicketDedup` por último) e `GuardPolicy` na montagem
- [x] Dois `create_ticket` paralelos geram um ticket só (de ponta a ponta, inclusive com paralelismo real)
- [ ] `REFERENCE_DATE` suportada (feito no `cli`; falta no `mcp-orders`, na E5)
- [ ] Primeira execução de ponta a ponta com a API (`:cli:run`)

### E7 — Guardas [M] · tag `etapa-7`
- [x] `IdentityGate`, `Redaction`, `RedactResults`, `TicketDedup`, `MentionTicket` (ligadas na montagem, na E6)
- [x] Padrões de documento, cartão e e-mail num lugar só (`Patterns`)
- [x] `GuardPolicy`: ferramenta destrutiva sem guarda é rejeitada na montagem (chamada na montagem, na E6)
- [x] Dois `create_ticket` paralelos geram um ticket só (no nível da guarda; de ponta a ponta na E6)

### E8.1 — Saída tipada [M] · tag `etapa-8.1`
- [x] `SupportAnswer` completo no `contract`
- [x] Validação em código (ticket inventado, confiança, fontes, intenção)
- [x] Esquema `answer_customer`

### E8.2 — Revisor e caso de uso [M] · tag `etapa-8.2`
- [x] `ReviewLoop` com no máximo 2 revisões e escalação por código
- [x] `AnswerCustomer` devolvendo `Flow<AgentEvent>`, um por pergunta

### E9.1 — Eventos, trace e custo [D] · tag `etapa-9.1`
- [x] Guia `docs/stages/9-1-trace.pt-BR.md`
- [ ] `runId` e `parentRunId` nos eventos, com o pai propagado pelo contexto da coroutine
- [ ] `TraceReport` (árvore, custo por agente, mais lentos) e arquivo JSON de trace (`TraceFiles`)
- [ ] Sem estado global

### E9.2 — Evals [M + D] · tag `etapa-9.2`
- [x] Golden dataset de 15 casos, neutralizado (com verificação de vazamento, `must_not_contain`)
- [x] Pontuação e relatório com as 6 métricas (roteamento, groundedness, fatos, fontes, precisão e recall de escalação) + uma métrica de vazamento, latência e tokens
- [ ] `EvalRunner` continua depois de um caso com erro
- [ ] Relatório real em `reports/` (precisa da chave)

### E10 — Fechamento da fase 1 [M] · tag `fase-1`
- [ ] README com diagrama, como rodar, métricas e um trecho de trace
- [ ] ADRs 0001 a 0003
- [ ] Critérios de sucesso da spec conferidos e todas as tags existem

---

## Fase 2 — Interface (spec e plano próprios, depois da fase 1)

### E11 — Servidor Ktor
- [ ] Spec e plano da fase 2
- [ ] `server`: `POST /chat` com streaming de eventos, chave só no servidor
- [ ] Teste do contrato HTTP

### E12 — App Compose Multiplatform
- [ ] Alvos escolhidos (Android + Desktop primeiro; iOS e Web depois, conferindo a maturidade atual)
- [ ] `contract` habilitado para os alvos do app
- [ ] Chat com fontes citadas, selo de escalado e árvore do trace ao vivo
- [ ] Servidor acessível pelos clientes (emulador, simulador, aparelho real)

---

## Entrega final

### E13 — Apresentação didática
- [ ] Formato definido (deck ou página)
- [ ] Títulos das etapas com o nome real do conceito e a sigla por extenso, seguido de um subtítulo em linguagem simples (ver `docs/presentation/glossary.pt-BR.md`)
- [ ] Um bloco por etapa: conceito, analogia, diagrama, código real da tag, demo, o que observar
- [ ] Arquitetura completa e números da avaliação
- [ ] GIF ou vídeo da demo

### E14 — Artigo para publicar (último item)
- [ ] Plataforma e público escolhidos
- [ ] Roteiro: problema, arquitetura, o que foi construído etapa a etapa, números da avaliação, lições aprendidas
- [ ] Rascunho com trechos reais de código tirados das tags
- [ ] Imagens: diagrama da arquitetura, GIF da demo, tabela de métricas
- [ ] Revisão sua e ajustes
- [ ] Publicação (só com a sua confirmação) e link no README
