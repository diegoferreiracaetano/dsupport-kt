# ADR 0003 — Cliente do LLM

Status: aceita (etapa 2)

## Contexto

O `core` só conhece a porta `LlmClient`. Falta decidir como o primeiro adaptador, o da Anthropic, fala com a API, e como ele lida com o raciocínio do modelo e com a saída estruturada.

## Decisões

1. **HTTP direto com Ktor, em `commonMain`, em vez do SDK oficial.** O SDK oficial da Anthropic para Kotlin é o de Java, que só roda na JVM, e o módulo `adapters` é multiplataforma. O custo é manter o mapeamento JSON à mão; o benefício é portabilidade e um protocolo transparente. Se um dia o SDK for preferível, ele entra em `jvmMain` atrás da mesma porta, sem tocar no `core`.
2. **`thinking` sempre desligado.** O Claude Haiku 5.5 liga o raciocínio por padrão, e os blocos de raciocínio teriam que voltar ao histórico sem alteração. O `Message` neutro não guarda blocos específicos de um provedor, então o cliente envia `thinking: {"type": "disabled"}` e falha com `LlmApiException` se aparecer um bloco de raciocínio. Consequência: modelos que não aceitam desligar o raciocínio, como o Claude Opus 5.5, dão erro 400 com este cliente.
3. **Saída estruturada por `output_config.format` (`json_schema`), não por `tool_choice` forçado.** Forçar uma ferramenta dá erro 400 em vários modelos. O formato nativo funciona em todos, e a resposta vem como texto JSON que o cliente converte em objeto. Limitação do esquema: nada de `minimum`/`maximum`, então faixas como `confidence` entre 0 e 1 são validadas no nosso código.
4. **Repetição só para 429 e 5xx**, até `maxRetries` (2 por padrão), com espera crescente (500 ms, 1 s). Erros 400, 401, 403 e 404 falham na hora, porque repetir não os resolve.
5. **Mensagens do mesmo papel em sequência viram uma só**, com os `tool_result` antes do texto, porque a API exige papéis alternados e quer os resultados do mesmo turno juntos.

## Consequências

- Trocar de provedor é escrever outro adaptador que passe em `LlmClientContract`; o `core` não muda.
- Usar um modelo que exija raciocínio ligado pede uma evolução do `Message` (um campo opaco por provedor) antes de funcionar.
