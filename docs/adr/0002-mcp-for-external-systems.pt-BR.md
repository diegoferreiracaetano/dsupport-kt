# ADR 0002 — MCP para sistemas externos

[English](0002-mcp-for-external-systems.md) | [Português](0002-mcp-for-external-systems.pt-BR.md)

Status: aceita (etapa 5)

## Contexto

Pedidos e tickets pertencem a sistemas com dono e ciclo de versão próprios. O agente precisa deles como ferramentas, mas não deve ser reconstruído toda vez que um desses sistemas muda ou que um novo aparece.

## Decisões

1. **Sistemas externos com dono próprio ficam atrás do MCP (Model Context Protocol), como processos separados.** Pedidos e tickets rodam como `mcp-orders` e `mcp-tickets`. A lógica interna do agente, como a busca na base de conhecimento, continua local.
2. **A porta `Tool` esconde a diferença.** O `McpToolProvider` transforma ferramentas remotas em `Tool`, então o `Agent` é idêntico com ferramentas locais ou remotas.
3. **O SDK oficial do Kotlin, sobre stdio.** O SDK fica atrás de `McpConnector` e `McpConnection`, em uma única classe (`StdioMcpConnector`). O `McpToolProvider` é testado com mocks, offline.
4. **Uma falha remota nunca derruba o agente.** Um timeout, um servidor morto ou uma exceção viram um `ToolResult` com `isError = true`; um servidor que não inicia lança `McpServerException` com o nome dele.
5. **Servidores são configuração.** `config/mcp-servers.json` os lista, escolhido por `MCP_SERVERS_FILE`; adicionar um não muda o agente. Dois servidores com a mesma ferramenta são erro de configuração.

## Consequências

- Em um servidor stdio a saída padrão é o protocolo, então o `McpToolServer` redireciona `System.out` para `System.err`.
- Cada servidor é um processo filho: o provider os fecha ao encerrar, e a queda de um servidor é vista pelo agente como erro de ferramenta.
- As ferramentas locais continuam sendo o padrão (sem `MCP_SERVERS_FILE`), então a avaliação e os testes não dependem de processos filhos.
