# Multiagentes na prática: RAG, MCP, guardrails e evals do zero em Kotlin

[English](article.md) | [Português](article.pt-BR.md)

> Rascunho para revisão. Os trechos reais vêm das tags do repositório (`git checkout etapa-N`).

## O problema

Clientes perguntam sobre pedidos, devoluções e políticas. A maioria das perguntas tem resposta em uma central de ajuda ou em um sistema de pedidos, e algumas precisam de uma pessoa. Eu queria um assistente que responda o primeiro grupo, passe o segundo a um humano e nunca invente um fato. Também queria entender cada peça, então construí sem framework de agentes.

## A arquitetura em uma imagem

`contract` ← `core` ← `adapters` ← aplicações (`cli`, `eval`, `server`, servidores MCP, `app`). O core é dono das portas (`LlmClient`, `Tool`, `ToolProvider`) e os adapters as implementam. Como `contract` e `core` são Kotlin Multiplatform, um cliente em outra plataforma pode reaproveitá-los.

## O que construí, etapa por etapa

1. **Laço do agente.** O modelo decide, o código age. O laço é limitado por um número máximo de turnos, e uma ferramenta desconhecida devolve erro em vez de quebrar.
2. **Cliente de LLM.** Uma porta, um adapter da Anthropic sobre HTTP puro. O mapeamento entre nossas mensagens e o JSON do provedor fica isolado, então um segundo provedor é um novo adapter.
3. **Ferramentas.** Status do pedido, rastreio da entrega e criação de ticket. Uma ferramenta reporta a falha como um resultado que o modelo consegue ler.
4. **RAG.** Os artigos da central de ajuda são divididos em trechos, pontuados com TF-IDF e devolvidos só acima de um corte de relevância. A resposta cita as fontes.
5. **MCP.** As ferramentas de pedido e ticket foram para servidores separados com o SDK oficial do Kotlin. O agente continua vendo um `Tool`. Um servidor morto vira resultado de erro, nunca trava.
6. **Orquestração multiagente.** Um orquestrador delega para especialistas de conhecimento, pedidos e escalonamento. Chamadas do mesmo turno rodam em paralelo e mantêm a ordem.
7. **Guardrails.** Código em volta do modelo: confere número do pedido e e-mail antes da consulta, oculta dados pessoais e junta tickets duplicados. O modelo não vê nem pula essas regras.
8. **Saída estruturada e revisor.** A resposta final é um formulário JSON validado por código (um id de ticket inventado é rejeitado). Um segundo modelo revisa, com no máximo duas revisões, e depois o código escala para um humano.
9. **Observabilidade e evals.** Cada passo é um evento com id de execução e id do pai, o que dá uma árvore de rastro e o custo por agente. Um dataset dourado de 15 casos, rodado três vezes cada, pontua roteamento, fundamentação, fatos, fontes, vazamentos e escalonamento.
10. **Servidor e app.** Um servidor Ktor transmite os eventos como Server-Sent Events, e um app Compose Desktop desenha a conversa e o rastro ao vivo.

## Os números

| Métrica | Primeira rodada | Depois das correções |
|---|---|---|
| Fatos esperados presentes | 93% | 100% |
| Fontes esperadas citadas | 86% | 100% |
| Precisão de escalonamento | 80% | 100% |
| Fallback da revisão | 9% | 0% |
| Latência média | 11,0 s | 9,8 s |
| Tokens por caso | 9.510 | 9.053 |

Os evals acharam três problemas reais: uma regra de confiança que tratava pedir um detalhe ao cliente como incerteza, um revisor rígido demais e perguntas de "como fazer" indo para o especialista errado. Corrigi cada um, rodei de novo e guardei o registro em `docs/evals/improvement-log.pt-BR.md`.

## Lições aprendidas

- **Coloque as regras no código, não no prompt.** Guards e validadores são determinísticos e testáveis.
- **Meça antes e depois.** Sem os evals eu teria confiado em uma demo.
- **Mantenha o modelo numa rédea curta.** Turnos limitados, revisões limitadas e um humano como último passo.
- **Portas deixam o custo visível.** Com dublês nas portas, toda a suíte de testes roda offline.
- **Cuide do custo.** Rodadas reais custam centavos, mas evals repetidos somam; avise o custo antes.

## Rodar

O README do repositório explica como rodar os testes, o assistente no terminal, o servidor e o app.
