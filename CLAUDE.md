# CLAUDE.md

Stable, slow-changing context for working in this repo. For volatile details
(what's done, what's next) see [docs/roadmap.md](docs/roadmap.md). For the "why"
behind choices see [docs/decisions.md](docs/decisions.md).

## What this is

**Inquisitor** is an LLM-driven integration-testing tool. You write integration
tests as human-readable markdown scenarios; an LLM-backed harness (Spring AI)
drives the application under test and verifies the outcome. It ships as a Spring
Boot 4 starter so a consumer adds one test dependency and annotates a test class.

Base package: `io.inquisitor`. Group: `io.inquisitor`.

## Module map

| Module | Role |
|--------|------|
| `buildSrc/` | Gradle convention plugins (`inquisitor.java-conventions`, `.spring-conventions`, `.publish-conventions`) |
| `inquisitor-harness` | Core scenario execution; Spring AI `ChatClient` orchestration. Parses markdown scenarios (flexmark) and drives the app. |
| `inquisitor-harness-starter` | Spring Boot autoconfiguration for the harness |
| `inquisitor-harness-junit` | JUnit 5 extension that runs scenarios as tests |
| `inquisitor-harness-junit-starter` | Autoconfiguration for the JUnit extension |
| `inquisitor-mock` / `inquisitor-mock-starter` | Reserved (mock server); not yet implemented |
| `inquisitor-demo-db-starter` | Zero-config local Postgres via Testcontainers + Flyway |
| `inquisitor-demo` | Banking REST demo app + scenario tests; the reference consumer |

Consumer dependency direction (test scope):
`inquisitor-demo` → `inquisitor-harness-junit-starter` → `…-junit` →
`…-harness-starter` → `…-harness` → Spring AI.

The root project is a `java-platform` BOM published as `inquisitor-bom`.

## Build

- **Gradle 9.5.1** (Kotlin DSL), Java toolchain **26**. Configuration cache,
  build cache, and parallel builds are on (`gradle.properties`).
- Versions are centralized in `gradle/libs.versions.toml`. Add/upgrade deps
  there, not inline.
- Convention plugins live in `buildSrc`; module `build.gradle.kts` files apply
  one of them rather than repeating config. Spring modules apply
  `inquisitor.spring-conventions` (imports the Spring Boot + Spring AI BOMs).
- Library modules compile with `options.release = 21`. **`inquisitor-demo` is
  the exception**: it compiles with `release = 26` and `--enable-preview`
  (also passed to `test` and `bootRun`).

Common commands:

```bash
./gradlew build              # compile + test everything
./gradlew check              # tests + verification
./gradlew :inquisitor-demo:test       # run the demo's scenario suite
./gradlew :inquisitor-demo:bootRun    # run the demo app (local profile)
```

## Running the demo

The `local` profile is active by default. The `inquisitor-demo-db-starter`
autoconfiguration starts a Postgres Testcontainer (`postgres:17-alpine`, reuse
enabled) under the `local`/`unitTest` profiles and wires it in via
`JdbcConnectionDetails` before Spring Boot's `DataSourceAutoConfiguration`.
Flyway migrations run from `classpath:db/migration`. No manual DB setup — just
`./gradlew :inquisitor-demo:bootRun`. Docker must be available for Testcontainers.

Scenario tests: a test class is annotated `@SpringBootTest(webEnvironment =
RANDOM_PORT)` + `@InquisitorTest(scenarioDirs = "classpath:scenarios/")`, and
the markdown files under `src/test/resources/scenarios/` are the test cases.

## Conventions

Code style and framework usage are governed by the **`java-developer` skill**
(`.claude/skills/java-developer/SKILL.md`) — treat it as authoritative. Highlights:

- Java 26: records, sealed interfaces + records for domain ADTs, pattern-matching
  `switch`, `_` unnamed variables, virtual threads. Prefer blocking + virtual
  threads over reactive.
- **JSpecify** null safety: every `package-info.java` is `@NullMarked`; mark
  nullable params/returns with `@Nullable`. Return `Optional`, never `null`.
- **Spring Data JDBC** by default (not JPA). **Flyway** for migrations.
  Config in **`application.yml`** only (never `.properties`).
- **Lombok**: `val` for effectively-final locals, `@RequiredArgsConstructor`,
  `@Slf4j`, etc. Don't mark service beans `final` (breaks CGLIB proxies).
- **`RestTestClient`** for HTTP-layer tests (not MockMvc / WebTestClient /
  TestRestTemplate). **Testcontainers** for data-layer tests, not mocks.
- Spring Boot 4 / Framework 7 APIs: Jackson 3 (`tools.jackson`, not
  `com.fasterxml.jackson`); built-in `@Retryable` / `@ConcurrencyLimit` (no
  Resilience4j); `@ImportHttpServices` for HTTP clients; native API versioning
  via the `version` attribute (no `/v1/` paths).
- Prefer autoconfiguration over manual `@Bean` declarations.

## Agents

Three project agents escalate by scope: `junior-developer` (well-scoped tasks),
`senior-developer` (cross-cutting / architecture-level), `principal-developer`
(highest-impact, long-term decisions). All follow the `java-developer` skill.

## Source layout note

`inquisitor-harness` is implemented: `model/` (Scenario, Step, verdict records),
`parser/` (flexmark markdown → `Scenario`), `tool/` (`HttpRequestTool`, `SqlTool`
+ named registries), `executor/` (`ScenarioExecutor`, `ChatClientStepEvaluator`,
system prompt). `inquisitor-harness-starter` autoconfigures it. The ergonomic
JUnit layer is implemented in `inquisitor-harness-junit` (`@Harness` on the test
class + one `@Scenario` method per scenario; `@Scenario` is a `@TestTemplate` that
reports one sub-test per step) and re-exported by
`inquisitor-harness-junit-starter`. The demo exercises both surfaces: the gated
`ScenarioTests` (standalone, JUnit-free contract) and the `@Harness`/`@Scenario`
suite (`PositiveScenarioSuite`, bound to a style bucket by each subclass —
`ExplicitScenarioSuiteTest`, `CucumberScenarioSuiteTest`).

Demo scenarios live under `src/test/resources/scenarios/` split by purpose then
authoring style: `positive/{explicit,cucumber}/` (same scenarios written
prescriptively vs. in Gherkin Given/When/Then, to gauge how a model copes with
different engineering styles) and `negative/{explicit,cucumber}/` (reserved for
oracle-calibration fixtures — see `tasks/task-07-negative-scenarios.md`). See
[docs/roadmap.md](docs/roadmap.md).
