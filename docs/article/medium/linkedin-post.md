# LinkedIn post (EN)

I built a multi-agent AI assistant from scratch in Kotlin, with no framework, and wrote the step-by-step.

What is inside, with real code:
- Agent loop: the model decides, your code acts
- RAG: answers only from sources, with a relevance cut-off
- MCP: tools served by separate programs (official Kotlin SDK)
- Multi-agent orchestration: an orchestrator and three specialists, in parallel
- Guardrails: rules the model can neither see nor skip
- Evals: 15 golden cases x 3 runs. Facts went from 93% to 100%, review fallback from 9% to 0%, for about 30 cents

One real question costs 12 model calls. I explain when NOT to use multiple agents too.

Article and code: github.com/diegoferreiracaetano/dsupport-kt

Which one is new to you: RAG, MCP, guardrails or evals?

#AI #Kotlin #LLM #RAG #MCP #SoftwareEngineering

---

# Post no LinkedIn (PT)

Construí do zero, em Kotlin e sem framework, um assistente de IA multiagente, e escrevi o passo a passo.

O que tem dentro, com código real:
- Agent loop: o modelo decide, o seu código age
- RAG: responde só com fontes, com corte de relevância
- MCP: ferramentas servidas por programas separados (SDK oficial do Kotlin)
- Orquestração multiagente: um orquestrador e três especialistas, em paralelo
- Guardrails: regras que o modelo não vê nem pula
- Evals: 15 casos dourados x 3 execuções. Fatos foram de 93% para 100%, fallback da revisão de 9% para 0%, por cerca de 30 centavos

Uma pergunta real custa 12 chamadas ao modelo. Explico também quando NÃO usar vários agentes.

Artigo e código: github.com/diegoferreiracaetano/dsupport-kt

Qual destes é novo para você: RAG, MCP, guardrails ou evals?

#IA #Kotlin #LLM #RAG #MCP #EngenhariaDeSoftware
