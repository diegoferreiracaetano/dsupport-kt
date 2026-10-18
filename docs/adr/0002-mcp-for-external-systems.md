# ADR 0002 — MCP for external systems

[English](0002-mcp-for-external-systems.md) | [Português](0002-mcp-for-external-systems.pt-BR.md)

Status: accepted (stage 5)

## Context

Orders and tickets belong to systems that have their own owner and their own release cycle. The agent needs them as tools, but must not be rebuilt every time one of those systems changes or a new one appears.

## Decisions

1. **External systems with their own owner go behind MCP (Model Context Protocol), as separate processes.** Orders and tickets run as `mcp-orders` and `mcp-tickets`. Internal agent logic, such as knowledge base search, stays local.
2. **The `Tool` port hides the difference.** `McpToolProvider` turns remote tools into `Tool`s, so the `Agent` is identical with local or remote tools.
3. **The official Kotlin SDK, over stdio.** The SDK sits behind `McpConnector` and `McpConnection`, in a single class (`StdioMcpConnector`). `McpToolProvider` is tested with mocks, offline.
4. **A remote failure never takes the agent down.** A timeout, a dead server or an exception becomes a `ToolResult` with `isError = true`; a server that does not start raises `McpServerException` naming it.
5. **Servers are configuration.** `config/mcp-servers.json` lists them, selected with `MCP_SERVERS_FILE`; adding one needs no change in the agent. Two servers exposing the same tool name is a configuration error.

## Consequences

- On a stdio server stdout is the protocol, so `McpToolServer` redirects `System.out` to `System.err`.
- Every server is a child process: the provider closes them on shutdown, and a server crash is seen by the agent as a tool error.
- Local tools remain the default (no `MCP_SERVERS_FILE`), so the evaluation and the tests do not depend on child processes.
