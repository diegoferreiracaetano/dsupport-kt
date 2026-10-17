# DSupport

[English](README.md) | [Português](README.pt-BR.md)

Assistente de suporte ao cliente multi-agente, construído à mão em Kotlin Multiplatform: Clean Architecture, ferramentas locais e via MCP, LLM trocável, guardas determinísticas, saída tipada, revisor e avaliação com relatório versionado.

> Em construção. Acompanhe o andamento em [CHECKLIST.pt-BR.md](CHECKLIST.pt-BR.md); a decisão de design está em [docs/superpowers/specs](docs/superpowers/specs).

## Requisitos

- JDK 21 (o projeto fixa a JVM do Gradle em `gradle/gradle-daemon-jvm.properties`; o `java` padrão da máquina pode ser outro).

## Testes

```bash
./gradlew test
```

## Documentação

Os documentos do projeto existem em inglês (`nome.md`) e em português (`nome.pt-BR.md`): o README, o checklist, os ADRs em `docs/adr` e os guias de etapa em `docs/stages`. O vocabulário da apresentação (nome real dos conceitos, significado em linguagem simples e analogia) está em `docs/presentation`. A spec de design e o plano de implementação em `docs/superpowers` são documentos de trabalho, mantidos só em português.
