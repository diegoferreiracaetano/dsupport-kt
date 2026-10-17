# Glossário da apresentação

[English](glossary.md) | [Português](glossary.pt-BR.md)

**Regra:** todo título de etapa usa o **nome real do conceito**, com a sigla por extenso na primeira vez, seguido de um subtítulo em linguagem simples. O objetivo é que a plateia saia sabendo as palavras que o mercado usa (RAG, MCP, guardrails, evals), e não só uma analogia. A analogia apoia o nome, nunca o substitui.

Formato de um título: `Etapa 4 — RAG (Retrieval-Augmented Generation): respondendo com consulta`.

| Etapa | Conceito (nome real) | Por extenso | Em linguagem simples | Analogia |
|---|---|---|---|---|
| 1 | **Agent loop** (loop agêntico) | o ciclo que faz um modelo virar *agente* | O modelo decide, o seu código age, o resultado volta, até haver uma resposta | Um gerente que só dá ordens e um assistente que faz o trabalho |
| 2 | **LLM client** (cliente do LLM) | LLM = Large Language Model | Uma porta para falar com qualquer modelo de linguagem, com um adaptador por provedor | Tomada e plugue: troca o cérebro, mantém o sistema |
| 3 | **Tools** (tool use, function calling) | | Funções que o modelo pode pedir para o código executar | Dar mãos ao assistente: consultar um pedido, abrir um ticket |
| 4 | **RAG** | Retrieval-Augmented Generation | Buscar documentos confiáveis antes, responder com o que achou e citar a fonte | Uma prova com consulta ao manual |
| 4 | **Embeddings**, **chunking**, **TF-IDF** | | Transformar texto em números para achar trechos parecidos; dividir artigos em trechos; um esquema simples de peso de palavras | Um mapa onde significados parecidos ficam perto |
| 5 | **MCP** | Model Context Protocol | Um padrão aberto para conectar ferramentas a agentes, em processos separados | USB-C: qualquer ferramenta que fale o padrão encaixa em qualquer agente |
| 6 | **Multi-agent orchestration** (orquestrador e subagentes) | | Um agente coordenador delega a agentes especialistas, em paralelo quando dá | Uma recepção que manda cada pergunta ao especialista certo |
| 7 | **Guardrails** (guardas, hooks) | | Regras determinísticas impostas pelo código em volta do modelo: bloquear, corrigir, reescrever | Travas de segurança que o modelo não vê nem pula |
| 8.1 | **Structured output** (saída estruturada) e **validation** | | O modelo preenche um esquema JSON fixo; o código confere cada campo | A resposta em formulário, em vez de texto livre |
| 8.2 | **Producer–reviewer loop** (revisão por LLM, tentativas limitadas, escalação) | | Uma etapa propõe, outra revisa, um limite evita ciclos sem fim, um humano assume | Um editor lendo antes de sair |
| 9.1 | **Observability** (eventos, tracing, custo por agente) | | Cada passo, chamada de ferramenta e token fica registrado | Uma caixa-preta |
| 9.2 | **Evals** (avaliação, golden dataset) | | Um conjunto fixo de perguntas com respostas conhecidas, pontuado por métricas: acerto de roteamento, groundedness, precisão e recall de escalação | Uma prova com gabarito |
| 11 | **API server** (servidor) | | O assistente atrás de um endereço web, transmitindo seus eventos | Abrir as portas |
| 12 | **Client app** (app cliente) | | A tela de chat com fontes, selo de ticket e rastro ao vivo | A cara do assistente |

Mais termos: **escalation** (passar o caso a um humano), **groundedness** (cada afirmação tem uma fonte), **hallucination** (alucinação: um fato inventado), **prompt** (as instruções dadas ao modelo), **token** (a unidade que o modelo lê e pela qual cobra).
