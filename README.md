# DSupport

Assistente de suporte ao cliente multi-agente, construído à mão em Kotlin Multiplatform: Clean Architecture, ferramentas locais e via MCP, LLM trocável, guardas determinísticas, saída tipada, revisor e avaliação com relatório versionado.

> Em construção. Acompanhe o andamento em [CHECKLIST.md](CHECKLIST.md); a decisão de design está em [docs/superpowers/specs](docs/superpowers/specs).

## Requisitos

- JDK 21 (o projeto fixa a JVM do Gradle em `gradle/gradle-daemon-jvm.properties`; o `java` padrão da máquina pode ser outro).

## Testes

```bash
./gradlew test
```
