# Inquisitor — Project Plan

LLM-driven integration testing via human-readable markdown scenarios, distributed as a Spring Boot 4 starter.

## Module Structure

```
inquisitor/
├── build-logic/                         ← Gradle 9 convention plugins
├── gradle/libs.versions.toml            ← version catalog
├── inquisitor-core/                     ← pure Java; zero Spring/AI deps
├── inquisitor-spring-ai/                ← Spring AI orchestration + tool adapters
├── inquisitor-autoconfigure/            ← Spring Boot 4 auto-configuration
├── inquisitor-spring-boot-starter/      ← thin starter
└── inquisitor-demo/                     ← demo REST app + scenario tests
```

## Dependency Graph

```
inquisitor-demo
  └─ inquisitor-spring-boot-starter (testImplementation)
       └─ inquisitor-autoconfigure
            ├─ inquisitor-spring-ai
            │    └─ inquisitor-core
            └─ spring-boot-autoconfigure
```

## Base Package

`io.inquisitor`

## Tasks

| # | Task | File | Status |
|---|------|------|--------|
| 01 | Gradle 9 multi-module build scaffold | [task-01-gradle-scaffold.md](tasks/task-01-gradle-scaffold.md) | pending |
| 02 | `inquisitor-core` module | [task-02-core.md](tasks/task-02-core.md) | pending |
| 03 | `inquisitor-spring-ai` module | [task-03-spring-ai.md](tasks/task-03-spring-ai.md) | pending |
| 04 | `inquisitor-autoconfigure` module | [task-04-autoconfigure.md](tasks/task-04-autoconfigure.md) | pending |
| 05 | `inquisitor-spring-boot-starter` module | [task-05-starter.md](tasks/task-05-starter.md) | pending |
| 06 | `inquisitor-demo` module | [task-06-demo.md](tasks/task-06-demo.md) | pending |
| 07 | JUnit 5 extension | [task-07-junit5-extension.md](tasks/task-07-junit5-extension.md) | pending |
