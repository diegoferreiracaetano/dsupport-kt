# ADR 0001 — Clean Architecture em módulos

[English](0001-clean-architecture.md) | [Português](0001-clean-architecture.pt-BR.md)

Status: aceita (etapa 0)

## Contexto

O assistente precisa sobreviver a trocas de provedor de modelo, fonte de dados, protocolo e interface sem reescrever sua lógica.

## Decisões

1. **As dependências apontam para dentro.** `contract` (vocabulário) ← `core` (portas, loop do agente, guardas, revisão, caso de uso) ← `adapters` (Anthropic, RAG, arquivos JSON, MCP) ← aplicações (`cli`, `eval`, servidores MCP).
2. **As portas vivem no core.** `LlmClient`, `Tool`, `ToolProvider` e os gateways são interfaces do core; os adapters as implementam.
3. **`contract` e `core` são multiplataforma (JVM e Wasm)**, para que uma interface futura os compartilhe. O que precisa da JVM fica em `adapters/jvmMain` ou nas aplicações.
4. **Os testes usam dublês nas portas.** Nenhum teste chama um modelo real; a avaliação é o único lugar que chama, sob demanda.

## Consequências

- Trocar o LLM, a base de conhecimento ou o transporte das ferramentas é um novo adapter, nunca uma mudança no core.
- O build Wasm de `contract` e `core` é verificado no CI para manter o core portável.
