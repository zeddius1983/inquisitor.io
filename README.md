# inquisitor.io

[![build](https://github.com/zeddius1983/inquisitor.io/actions/workflows/build.yml/badge.svg)](https://github.com/zeddius1983/inquisitor.io/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.inquisitor/inquisitor-bom)](https://central.sonatype.com/namespace/io.inquisitor)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![site](https://img.shields.io/badge/site-inquisitor.io-fe8019?logo=firefox&logoColor=white)](https://inquisitor.io)
[![donate](https://img.shields.io/badge/donate-BTC%20%C2%B7%20USDT-d3869b?logo=bitcoin&logoColor=white)](https://inquisitor.io/#donate)

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

## Structured output

Each step's verdict comes back as JSON that deserializes into a `StepVerdict`. By
default the schema is appended to the prompt as format instructions, which a model
is free to ignore — a weak or heavily quantized actor may answer in prose instead,
and the harness degrades that step to a synthetic FAIL that says nothing about the
application under test.

Set the mode to `native` to send the schema to the provider as a structured-output
request, so it is enforced while decoding rather than merely asked for:

```yaml
inquisitor:
  harness:
    structured-output: native   # prompt (default) | native
```

llama.cpp implements this: it converts the schema to a GBNF grammar and masks any
token that would break it, so malformed JSON becomes impossible and an enum field
like `outcome` can only be `PASS` or `FAIL`. The hosted OpenAI-style Structured
Outputs APIs implement it too.

> **`native` is currently incompatible with tool calling on llama.cpp.** The harness
> always sends its `httpRequest`/`sqlQuery` tools, and llama.cpp cannot build a
> grammar that admits both a tool call and a schema-constrained answer. Measured
> against llama.cpp `b10850`:
>
> | Request | Result |
> |---------|--------|
> | schema, no tools | constrained JSON |
> | schema + tools | **HTTP 400** `failed to parse grammar` |
> | tools, no schema | tool call |
> | schema + tools + `tool_choice: none` | constrained JSON |
> | `json_object` + tools | constraint silently dropped — prose returned |
>
> So enabling `native` against llama.cpp aborts every scenario with a 400 at the
> first step. It is usable today only where the actor needs no tools, or against a
> provider that composes the two grammars.

Note also that llama.cpp supports only a subset of JSON Schema;
`examples/json_schema_to_grammar.py` in the llama.cpp tree will tell you offline
whether a schema converts cleanly.

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

### Optional: supervised execution

Enable refinement to let the judge ask the actor to correct missing actions,
unsupported conclusions, or malformed verdict JSON within the current step:

```yaml
inquisitor:
  harness:
    evaluation:
      refinement:
        enabled: true           # false by default
        max-corrections: 2       # additional actor turns, shared by JSON and execution repair
```

The actor continues with the same client, tools, conversation, and cumulative
tool ledger. Corrective writes are allowed; the reviewer checks required actions,
assertions, and ordering. A supported application FAIL is accepted immediately.
The reviewer must stop if correction would invalidate the experiment or require
a fresh fixture; automatic scenario restarts are not implemented.
The correction limit counts additional actor turns, not individual tool calls.
The reviewer runs after the actor returns an answer, so it cannot interrupt
extra tool calls inside an unfinished actor turn.

Exhausted corrections, reviewer failures, and actor/tool call failures produce an
unresolved execution and abort the scenario. They fail JUnit even under
`@Scenario(expect = FAIL)`; they never count as detected application defects.
`evaluation.enabled` and `refinement.enabled` select exclusive modes. With only
evaluation enabled, the judge performs observation-only scoring. With refinement
enabled, it takes precedence: the reviewer guides the actor and its final decision
is recorded directly; the observation-only evaluation step is not created or run,
and no evaluation lifecycle or score event is emitted.

HTML, Markdown, and JSON reports preserve the raw answer, new tool calls, feedback,
and timing for each attempt under one logical step. They distinguish initial
acceptance, acceptance after correction, and unresolved execution. These are
measurements of the actor/reviewer team; the reviewer participates in the result.
Raw responses and tool traces can contain application data, so treat report files
with the same care as test logs. A custom `StepReviewer` bean can replace the
default reviewer while retaining the bounded execution and reporting.

The judge client includes Spring AI's `SimpleLoggerAdvisor`. Temporarily
enable its DEBUG logger to inspect the judge request and response:

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor: DEBUG
```

The actor uses the same advisor and logger, so this shows both roles; judge requests
are identifiable by their `You are a test-trace auditor` prompt. In the demo, use
`INQUISITOR_LLM_LOG=DEBUG` and select plain harness logging because Markdown mode's
exclusive console intentionally suppresses unmarked framework diagnostics. Prompt
logs can contain scenario data and real tool results, so leave this disabled outside
trusted diagnostics.

```bash
INQUISITOR_HARNESS_LOGGING_FORMAT=plain INQUISITOR_LLM_LOG=DEBUG \
  ./gradlew :inquisitor-demo:evaluate
```

### llama.cpp and Qwen 3.6 MTP troubleshooting

When llama.cpp hosts a Qwen 3.6 judge, especially an MTP variant, check the
logged request before attributing an unrelated verdict to Inquisitor. Upstream
reports cover Qwen 3.6 hybrid/recurrent prompt-cache and checkpoint handling
([#22746](https://github.com/ggml-org/llama.cpp/issues/22746),
[#21831](https://github.com/ggml-org/llama.cpp/issues/21831)) and draft-MTP
changing deterministic output even with prompt caching disabled
([#23335](https://github.com/ggml-org/llama.cpp/issues/23335)). One possible
symptom is cross-request contamination: the logged request contains only the
current step, but the model's reasoning or verdict substitutes content from a
previous step.

If this happens, restart llama.cpp or unload and reload the judge, disable MTP
speculative decoding, and rerun only the affected scenario. If the clean process
works with MTP disabled, keep MTP off for the judge or update the llama.cpp
runtime and retest. A non-MTP build or a judge from another model family is the
safer fallback.

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
