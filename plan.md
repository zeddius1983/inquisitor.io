# Inquisitor — Project Plan

LLM-driven integration testing via human-readable markdown scenarios, distributed as a Spring Boot 4 starter.

## Module Structure

```
inquisitor/
├── buildSrc/                            ← Gradle 9 convention plugins
├── gradle/libs.versions.toml            ← version catalog
├── inquisitor-harness/                  ← Spring AI orchestration; core scenario execution
├── inquisitor-harness-starter/          ← Spring Boot autoconfiguration for harness
├── inquisitor-harness-junit/            ← JUnit 5 extension for scenario test execution
├── inquisitor-harness-junit-starter/    ← Spring Boot autoconfiguration for JUnit extension
├── inquisitor-mock/                     ← (reserved) mock server module
├── inquisitor-mock-starter/             ← (reserved) Spring Boot autoconfiguration for mock
└── inquisitor-demo/                     ← demo REST app + scenario tests
```

## Dependency Graph

```
inquisitor-demo
  └─ inquisitor-harness-junit-starter (testImplementation)
       └─ inquisitor-harness-junit
            └─ inquisitor-harness-starter
                 └─ inquisitor-harness
                      └─ spring-ai (via BOM)

inquisitor-mock-starter  (reserved)
  └─ inquisitor-mock     (reserved)
```

## Base Package

`io.inquisitor`

## Tasks

| # | Task | File | Status |
|---|------|------|--------|
| 01 | Gradle 9 multi-module build scaffold | [task-01-gradle-scaffold.md](tasks/task-01-gradle-scaffold.md) | pending |
| 02 | `inquisitor-harness` module | [task-02-harness.md](tasks/task-02-harness.md) | pending |
| 03 | `inquisitor-harness-starter` module | [task-03-harness-starter.md](tasks/task-03-harness-starter.md) | pending |
| 04 | `inquisitor-harness-junit` + `inquisitor-harness-junit-starter` | [task-04-harness-junit.md](tasks/task-04-harness-junit.md) | pending |
| 05 | `inquisitor-demo` module | [task-05-demo.md](tasks/task-05-demo.md) | pending |
| 06 | `inquisitor-mock` + `inquisitor-mock-starter` (reserved) | [task-06-mock.md](tasks/task-06-mock.md) | pending |
