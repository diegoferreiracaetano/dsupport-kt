# DSupport

[English](README.md) | [Português](README.pt-BR.md)

A multi-agent customer support assistant, hand-built in Kotlin Multiplatform: Clean Architecture, local and MCP tools, a swappable LLM, deterministic guards, typed output, a reviewer, and an evaluation with a versioned report.

> Work in progress. Follow the progress in [CHECKLIST.md](CHECKLIST.md); the design decisions are in [docs/superpowers/specs](docs/superpowers/specs).

## Requirements

- JDK 21 (the project pins the Gradle JVM in `gradle/gradle-daemon-jvm.properties`; the machine's default `java` can be a different version).

## Tests

```bash
./gradlew test
```

## Documentation

Project documents exist in English (`name.md`) and Portuguese (`name.pt-BR.md`): the README, the checklist, the ADRs in `docs/adr` and the stage guides in `docs/stages`. The presentation vocabulary (technical term, didactic name and analogy) is in `docs/presentation`. The design spec and the implementation plan under `docs/superpowers` are working documents, kept in Portuguese only.
