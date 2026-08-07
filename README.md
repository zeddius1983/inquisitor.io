# inquisitor.io

[![build](https://github.com/zeddius1983/inquisitor.io/actions/workflows/build.yml/badge.svg)](https://github.com/zeddius1983/inquisitor.io/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.inquisitor/inquisitor-bom)](https://central.sonatype.com/namespace/io.inquisitor)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

**LLM-driven integration testing for Spring applications.**

*AI-powered API testing · natural language integration testing.*

Write your integration tests as human-readable markdown scenarios. An
LLM-backed harness (built on [Spring AI](https://spring.io/projects/spring-ai))
reads each scenario, drives your running application through real HTTP and SQL,
and verifies the outcome — step by step.

Inquisitor ships as a Spring Boot 4 starter: add one test dependency, annotate a
test class, and drop your scenarios under `src/test/resources/scenarios/`.

```markdown
# Transfer between accounts

## Open two accounts
**Intent:** create a source and a destination account
...

## Move money
**Intent:** transfer 50 from the first account to the second
...

**Expected response**
- the source balance is reduced by 50
- the destination balance is increased by 50
```

```java
@Harness
@SpringBootTest(webEnvironment = RANDOM_PORT)
@RequiresLlm
class ScenarioSuiteTest {
    @Scenario void transferBetweenAccounts() {}
}
```

Each `## Step` is reported as its own sub-test (like a parameterized test's
invocations); the first failing step fails and the rest are skipped.
`@RequiresLlm` skips the suite when no model is configured, so it stays out of a
plain CI build (see [Building](#building)).

## How it works

- You describe **intent** and **expected outcome** in markdown — not request
  bodies and assertions in code.
- The harness hands the scenario to an LLM with two tools: `httpRequest` (drives
  your app over HTTP) and `sqlQuery` (inspects the database). The model decides
  what to call and judges each step's result.
- All steps in a scenario share one conversation, so an id created in step 1
  flows naturally into step 3 — no templating or regex.

> [!WARNING]
> **An LLM drives and judges these tests, and LLMs are non-deterministic.** A
> scenario can pass on one run and fail on the next, and a verdict is only as
> trustworthy as the model behind it. Treat Inquisitor as a complement to
> deterministic tests, not a replacement, and review failures before acting on
> them — a red step may be a real regression, model flakiness, or an ambiguous
> scenario.

## Modules

| Module | Role |
|--------|------|
| `inquisitor-harness` | Core scenario execution; Spring AI `ChatClient` orchestration. Parses markdown scenarios (flexmark) and drives the app. |
| `inquisitor-harness-starter` | Spring Boot autoconfiguration for the harness. |
| `inquisitor-harness-junit` | JUnit 5 layer: `@Harness` on the class + one `@Scenario` method per scenario, each step a sub-test. |
| `inquisitor-harness-junit-starter` | Autoconfiguration for the JUnit layer — the single dependency a consumer needs. |
| `inquisitor-harness-openapi` / `-starter` | Optional OpenAPI discovery: injects your app's spec into the prompt so scenarios can be natural-language intent. Off unless enabled. |
| `inquisitor-harness-evaluation` / `-starter` | Optional step evaluation (LLM-as-judge): a separate judge model scores each verdict against the recorded tool trace. Off unless enabled. |
| `inquisitor-harness-evaluation-report` | Writes the evaluation report (HTML by default; markdown/json selectable) when a report directory is configured. Shipped by the evaluation starter; excludable for score-only runs. |
| `inquisitor-logback-markdown` | Reusable Flexmark-backed `%mdMsg` and `%mdEvent` Logback converters for ANSI-styled console Markdown. |
| `inquisitor-logback-markdown-starter` | Automatic shared or marker-only Markdown rendering for compatible Spring Boot Logback consoles. |
| `inquisitor-bom` | Platform BOM aligning the Inquisitor module versions. |
| `inquisitor-demo` | Banking REST demo app + scenario tests; the reference consumer. |

`inquisitor-mock` / `inquisitor-mock-starter` are reserved for a future mock
server and not yet implemented.

## Getting started

The libraries run on **Java 21+** (building this repo itself needs a Java 26
toolchain — see [Building](#building)). Add the JUnit starter and the BOM to
your build (test scope):

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone") // Spring AI 2.0.0-RC1, until its GA reaches Central
}

dependencies {
    testImplementation(platform("io.inquisitor:inquisitor-bom:<version>"))
    testImplementation("io.inquisitor:inquisitor-harness-junit-starter")
}
```

Point the harness at an OpenAI-compatible chat model via the standard
`spring.ai.openai.*` properties:

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8000
      api-key: not-needed-for-local
      chat:
        model: <your-model>
```

Then write a `@Harness` test class with one `@Scenario` method per markdown file
under `src/test/resources/scenarios/`. The scenario file is resolved from the
method name (`transferBetweenAccounts()` → `transfer-between-accounts.md`) or set
explicitly with `@Scenario("classpath:scenarios/custom.md")`.

Scenario suites are gated by `@RequiresLlm` so they only run when a model is
switched on — see [Building](#building) for the `INQUISITOR_LLM_IT` gate.

## Harness logging

Choose plain text (the default) or marker-tagged Markdown narration:

```yaml
inquisitor:
  harness:
    logging:
      format: markdown # plain | markdown
```

**Plain** keeps the output operational and compact: scenario and step lifecycle
at INFO, details at DEBUG. Enable the two narrow logger namespaces to see step,
tool, verdict, and evaluation diagnostics without framework-wide DEBUG logging:

```yaml
logging:
  level:
    io.inquisitor.harness.logging: DEBUG
    io.inquisitor.harness.evaluation.logging: DEBUG # when evaluation is enabled
```

**Markdown** emits the complete narrative at INFO as events tagged with the
portable `INQUISITOR_MARKDOWN` SLF4J marker: Powerline-style breadcrumbs for
scenario, step, model, progress, and status; HTTP and SQL tool calls with
syntax-highlighted payloads (JSON pretty-printed, authorization/cookie headers
redacted); judge results with model, score, and feedback. Rendering is optional
and layered:

- add no rendering dependency to keep readable raw Markdown in ordinary logs;
- add [`inquisitor-logback-markdown`](inquisitor-logback-markdown/README.md) and
  configure `%mdMsg{marked}` for manual Logback control;
- add
  [`inquisitor-logback-markdown-starter`](inquisitor-logback-markdown-starter/README.md)
  for automatic rendering: compatible console appenders switch to an exclusive
  marked-events-only stream (framework noise omitted), while file and structured
  appenders keep their existing behavior.

Colors use a muted Gruvbox Dark palette by default;
`inquisitor.logging.markdown.palette` selects `nord`, `catppuccin`, or
`tokyo-night`, and additional palettes plug in through the renderer module's
`MarkdownPaletteProvider` ServiceLoader SPI. Powerline caps want a patched font
with the Powerline symbol range; the underlying Markdown stays readable when
ANSI is disabled. The harness itself depends on neither optional Markdown module
and remains portable to other SLF4J backends.

> The harness is intended for tests against local services and logs HTTP bodies
> and SQL results without general-purpose content redaction. Use test data and
> test credentials; do not point verbose harness logging at production systems
> or production datasets.

## Optional: OpenAPI discovery

By default a scenario tells the model which endpoints to call. If your app exposes an
OpenAPI document, you can instead let the model **read the spec and choose the
endpoints itself**, so scenarios become pure natural-language intent — no paths or
request bodies.

Add the plugin starter and turn it on:

```kotlin
testImplementation("io.inquisitor:inquisitor-harness-openapi-starter")
```

```yaml
inquisitor:
  harness:
    openapi:
      enabled: true            # explicit opt-in
      # location:              # optional static spec (classpath:/file:/http:); omit to live-fetch
      # path: /v3/api-docs.yaml # live-fetch path appended to the app's base URL
      # target: app             # which registered HTTP target to fetch from
```

To enable discovery per test class instead of globally, annotate the class with
`@EnableOpenApiDiscovery` (use `@EnableOpenApiDiscovery(enabled = false)` to turn it
off for a subclass) — equivalent to the property but scoped to that class:

```java
@Harness(scenarioDir = "classpath:scenarios/")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@EnableOpenApiDiscovery
class MyScenarioTest { ... }
```

When enabled, an `OpenApiAdvisor` fetches the spec (lazily, from the running app at
`/v3/api-docs.yaml` by default, or your `location`) and injects it into the system
prompt. It's an explicit opt-in, so if the spec can't be obtained the run **fails
fast** rather than silently proceeding without it. The module is fully optional —
remove the dependency and the harness behaves exactly as before. The demo's
`IntentScenarioSuiteTest` (scenarios under `scenarios/intent/`) shows it in
action.

> The spec is sent to the model. With a remote model, treat a large or sensitive API
> description accordingly.

## Optional: step evaluation (LLM-as-judge)

The actor model that drives your app also judges each step — and can occasionally
rubber-stamp a verdict it never really checked. The evaluation module puts a
**second, independent judge model** behind it: every verdict is re-scored against
the recorded HTTP/SQL tool trace, so you can measure how trustworthy your oracle
is instead of assuming it.

```kotlin
testImplementation("io.inquisitor:inquisitor-harness-evaluation-starter")
```

```yaml
inquisitor:
  harness:
    evaluation:
      enabled: true                    # explicit opt-in
      base-url: http://localhost:8001  # the judge's own endpoint
      model: <judge-model>             # ideally a different family from the actor
      # api-key:                       # falls back to spring.ai.openai.api-key
```

Use a genuinely separate judge — a different model family on its own server, not
a self-judge — for scores that mean something. When `inquisitor.report.dir` is
set, the test session also writes a step-by-step evaluation report there (HTML by
default, grouped by suite and scenario; markdown and json renderers are
selectable). The module is fully optional and removable without touching the
core.

## Building

Requires a Java 26 toolchain (Gradle provisions it) and, to run the demo,
Docker/Podman for Testcontainers.

```bash
./gradlew build                       # compile + test everything
./gradlew :inquisitor-demo:bootRun    # run the demo app (local profile)
```

Every push and PR is verified by the
[`build`](.github/workflows/build.yml) workflow (`./gradlew build` on a JDK 26
runner with Docker for Testcontainers).

The scenario suites that actually call an LLM are annotated `@RequiresLlm`, which
skips them unless a model is configured — so a plain `./gradlew build` stays green
without a running model. Enable them either way:

```bash
# environment variable (authoritative when set)
INQUISITOR_LLM_IT=true ./gradlew :inquisitor-demo:test

# or a JUnit configuration parameter (committable in junit-platform.properties,
# or passed as a system property) — the fallback when the env var is unset
./gradlew :inquisitor-demo:test -Dinquisitor.harness.llm.enabled=true
```

`@RequiresLlm` is `@Inherited`, so a suite hierarchy declares the gate once on its
base. Resolution order: `INQUISITOR_LLM_IT` env var → `inquisitor.harness.llm.enabled`
config parameter → disabled.

The demo's `local` profile starts a Postgres Testcontainer automatically
(`postgres:17-alpine`, reuse enabled) and runs Flyway migrations — no manual
database setup.

## Documentation

- [docs/roadmap.md](docs/roadmap.md) — what's done and what's next.
- [docs/decisions.md](docs/decisions.md) — the "why" behind the design choices.
- [CLAUDE.md](CLAUDE.md) — repository context and conventions (also used by AI
  coding agents working on this codebase).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
