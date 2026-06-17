# Architecture

Project design and structure. For stable working context see
[../CLAUDE.md](../CLAUDE.md); for the rationale behind individual choices see
[decisions.md](decisions.md); for status see [roadmap.md](roadmap.md).

## Overview

Inquisitor is an **LLM-driven integration-testing tool**. Integration tests are
authored as human-readable markdown scenarios; an LLM-backed harness (Spring AI)
interprets each scenario, drives the application under test over HTTP, and
verifies the described outcome. It is packaged as a Spring Boot 4 starter so a
consumer adds one test-scoped dependency and annotates a test class.

```
        ┌─────────────────────────────────────────────────────────┐
        │  Consumer test module (e.g. inquisitor-demo)             │
        │                                                          │
        │  @SpringBootTest(RANDOM_PORT)                            │
        │  @InquisitorTest(scenarioDirs = "classpath:scenarios/")  │
        │  class ScenarioSuiteTest {}                              │
        └───────────────┬──────────────────────────────────────────┘
                        │ discovers *.md scenarios
                        ▼
        ┌──────────────────────────┐   parse    ┌────────────────────┐
        │ inquisitor-harness-junit │ ─────────► │ inquisitor-harness │
        │ (JUnit 5 extension)      │            │  parser (flexmark) │
        └──────────────────────────┘            │  executor          │
                        ▲                        │  Spring AI         │
                        │ verdict                │  ChatClient        │
                        │                        └─────────┬──────────┘
                        │                                  │ HTTP calls
                        │                                  ▼
                        │                        ┌────────────────────┐
                        └────────────────────────│ App under test     │
                                                 │ (RANDOM_PORT)      │
                                                 └────────────────────┘
```

## Module structure

Layered as `core → starter` pairs so each capability can be consumed either
directly (the library) or with zero-config Spring Boot autoconfiguration (the
starter).

| Module | Responsibility |
|--------|----------------|
| `inquisitor-harness` | Core engine. Markdown scenario parsing (flexmark), Spring AI `ChatClient` orchestration, and the executor that drives the app under test and renders a verdict. Depends on Spring AI + `spring-boot-starter-web`. |
| `inquisitor-harness-starter` | Spring Boot autoconfiguration for the harness. |
| `inquisitor-harness-junit` | JUnit 5 extension: discovers scenario files and runs each as a test, reporting pass/fail. |
| `inquisitor-harness-junit-starter` | Autoconfiguration for the JUnit extension; exposes the `@InquisitorTest` annotation surface. |
| `inquisitor-mock` / `inquisitor-mock-starter` | Reserved. Mock server for stubbing third-party dependencies during scenarios. Not yet implemented. |
| `inquisitor-demo-db-starter` | Reusable zero-config local Postgres (Testcontainers + Flyway), activated under `local`/`unitTest` profiles. |
| `inquisitor-demo` | Reference consumer: a banking REST service plus scenario tests. Validates the harness end-to-end and demonstrates intended usage. See [requirements.md](requirements.md). |

Root project is a `java-platform` BOM published as `inquisitor-bom` for version
alignment across consumers.

## Dependency direction

Test-scope chain from the consumer down to the engine:

```
inquisitor-demo (testImplementation)
  └─ inquisitor-harness-junit-starter
       └─ inquisitor-harness-junit
            └─ inquisitor-harness-starter
                 └─ inquisitor-harness
                      └─ Spring AI (ChatClient, OpenAI model) via BOM
```

The `*-mock` chain (`inquisitor-mock-starter → inquisitor-mock`) is reserved and
parallel; `inquisitor-demo-db-starter` is an independent app-support module.

## Build architecture

- **Gradle 9.5.1**, Kotlin DSL, single composite build.
- Convention plugins in `buildSrc/` are the shared build contract:
  - `inquisitor.java-conventions` — Java toolchain 26, `release = 21`, JUnit
    platform, junit-bom wiring.
  - `inquisitor.spring-conventions` — applies java-conventions + Spring
    dependency-management, importing the Spring Boot and Spring AI BOMs.
  - `inquisitor.publish-conventions` — `maven-publish` setup for library modules.
- Versions centralized in `gradle/libs.versions.toml`.
- Libraries target `release = 21` for broad consumer compatibility;
  `inquisitor-demo` is the deliberate exception at `release = 26` +
  `--enable-preview`. See [decisions.md](decisions.md).

## Demo application design

The demo (`io.inquisitor.demo`) is a conventional layered Spring Boot 4 service:

- **Web** (`web/`) — `AccountController`, `TransferController`, request/response
  DTOs (records, Jakarta Validation), and a `GlobalExceptionHandler` that maps
  domain exceptions to RFC 9457 `ProblemDetail` responses.
- **Service** (`service/`) — `AccountService` holds transactional business
  logic (deposit, withdraw, transfer, search) and defines domain exceptions
  (`AccountNotFoundException`, `InsufficientFundsException`).
- **Repository** (`repository/`) — Spring Data JDBC `CrudRepository`
  interfaces with derived/`@Query` search + count methods; pagination via
  `PageableExecutionUtils` over `List` + count.
- **Model** (`model/`) — immutable record aggregates (`Account`, `Transaction`,
  `TransactionType`) with optimistic locking (`@Version`) and `@CreatedDate`.
- **Persistence** — Postgres, Flyway migration `V1__init.sql`. Locally the DB is
  a Testcontainer supplied by `inquisitor-demo-db-starter` before Spring Boot's
  `DataSourceAutoConfiguration` runs, so no manual setup is required.

Every `package-info.java` is `@NullMarked` (JSpecify). Code style is governed by
the `java-developer` skill (`.claude/skills/java-developer/SKILL.md`).
