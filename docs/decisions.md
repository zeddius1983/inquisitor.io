# Decisions

The "why" behind choices that aren't obvious from the code. For current status
see [roadmap.md](roadmap.md); for stable repo context see
[../CLAUDE.md](../CLAUDE.md). Append new entries; don't rewrite history.

## Build & language

- **Gradle convention plugins in `buildSrc/`** (not `includeBuild("build-logic")`).
  Simpler for a single-repo build; one place defines the Java/Spring/publish
  conventions that every module applies.
- **Java toolchain 26, but libraries compile to `release = 21`.** Keeps the
  published library modules consumable on Java 21 while developing on 26. Only
  `inquisitor-demo` opts into `release = 26` + `--enable-preview`, because it's
  an app (not published) and exercises the newest language features.
- **Version catalog (`libs.versions.toml`)** is the single source of dependency
  versions; module builds reference `libs.*`, never hardcode versions.
- **Root project is a published BOM** (`inquisitor-bom`) so consumers get
  aligned versions of the Inquisitor modules. It declares `constraints` for the
  four published modules; coordinates are overridden to `inquisitor-bom` since the
  root project is named `inquisitor.io`.
- **Maven Central via the Central Portal, Vanniktech plugin.** Legacy OSSRH was
  shut down (June 2025), so publishing goes through `central.sonatype.com`. The
  `com.vanniktech.maven.publish` plugin (applied by `inquisitor.publish-conventions`)
  generates the sources/Javadoc jars, signs, builds the POM, and uploads in one
  step — far less boilerplate than hand-rolling `maven-publish` + signing + nmcp.
  Only the four harness modules + the BOM publish; the demo and the unimplemented
  mock modules don't. Account, `io.inquisitor` namespace (domain-verified), GPG
  key and token live outside the repo — see the README release runbook.

## Framework

- **Spring Data JDBC over JPA.** Explicit aggregates, AOT-processed queries,
  faster startup, no lazy-loading surprises. JPA only if entity count grows
  large enough to justify it.
- **Zero-config local run.** `inquisitor-demo-db-starter` autoconfigures a
  Postgres Testcontainer under the `local`/`unitTest` profiles and supplies a
  `JdbcConnectionDetails` bean *before* `DataSourceAutoConfiguration`, so a
  fresh clone runs with `./gradlew :inquisitor-demo:bootRun` and no manual
  database setup. Container reuse is enabled to keep restarts fast.
- **Spring AI as the harness engine.** Scenarios are natural-language markdown;
  an LLM interprets and drives the app, which is the whole premise of the tool.

## Harness

- **Step-by-step execution over shared chat memory.** Each `## Step` is one
  `ChatClient` call; all steps in a scenario share a `conversationId` backed by
  `MessageChatMemoryAdvisor`, so an id created in step 1 flows to later steps
  without templating or regex. A scenario with no `## Step` headings collapses to
  one implicit step.
- **Named registries, no privileged default.** `HttpTargetRegistry` /
  `DataSourceRegistry` are allow-lists: a tool call that omits the name resolves
  only when exactly one entry is registered, else it must name one. The app under
  test is auto-registered under the constant name `HarnessDefaults.APPLICATION`
  (`"app"`) — its `DataSource` by the starter, its HTTP target (random port) by
  the consumer test.
- **Tools baked into the `ChatClient` via `defaultTools(Object...)`.** Built-ins
  (`httpRequest`, `sqlQuery`) plus any user `ToolCallback`/`ToolCallbackProvider`
  beans are aggregated and passed through the single unified entry point; the
  `defaultToolCallbacks(...)` overloads are deprecated for removal in Spring AI
  2.0.0-RC1.
- **Model-dependent beans degrade gracefully.** `ChatClient`/executor beans are
  `@ConditionalOnBean(ChatModel.class)` and the autoconfig is ordered
  `afterName` the OpenAI chat autoconfig, so the context still starts (just
  without the harness) when no model is configured.
- **Dense local model, temperature 0.** `gemma-4-31B-it-QAT-Q4_0`; MoE models
  shortcut multi-step scenarios by answering from chat memory instead of calling
  the tool, producing hallucinated PASSes.
- **OpenAI client config via standard `OPENAI_*` env vars** (not Inquisitor-
  prefixed), since a consumer project may share the same chat model for other
  purposes. Uses the flattened `spring.ai.openai.chat.{model,temperature}`
  properties (the nested `chat.options.*` form is deprecated for removal).

## Testing

- **Scenarios as markdown test cases.** Human-readable `.md` files under
  `src/test/resources/scenarios/` are the integration tests. Each step separates
  intent from assertions: `**Intent:**` + a code fence for the action, an
  **Expected response** bullet list for what to verify. Request bodies are
  currently explicit (deterministic for the small local model); they'll move to
  natural-language-only once OpenAPI discovery lands.
- **Standalone harness proven in the demo before the JUnit layer.** Phase 5 wires
  the autoconfigured harness into `inquisitor-demo` with plain `@Test`-per-scenario
  methods that call `ScenarioExecutor.evaluate` and assert on `ScenarioResult`,
  rather than first building `@InquisitorTest`. This validates the JUnit-free
  public contract end-to-end (autoconfig → `ChatClient` → tools over real
  HTTP/Postgres → local model); the ergonomic JUnit layer is built on top later.
  The suite is gated `@EnabledIfEnvironmentVariable(INQUISITOR_LLM_IT=true)` so
  the normal `./gradlew build` stays green without a running model.
- **Controller + repository tracing via AOP aspects.** `ControllerTracingAspect`
  logs every `@RestController` call and `RepositoryTracingAspect` every Spring Data
  repository call (both `io.inquisitor.demo.aop.log`, `spring-boot-starter-aspectj`),
  each as `method(args) -> result`/`-> threw …`. Together they show the LLM-driven
  HTTP request *and* the persistence calls behind it, confirming the harness drives
  *real* traffic rather than fabricating tool results; aspects (vs per-method
  logging) keep controllers/repositories clean and also trace exception paths. The
  repository pointcut matches the Spring Data `Repository` marker via `target(...)`
  — a per-interface `this(...)` pointcut matched the inherited `CrudRepository`
  methods (`save`, `findById`) unreliably.

## JUnit layer

- **Annotations + extensions, no base class, no custom `TestEngine`.** A test
  class is annotated `@Harness` and each scenario is one `@Scenario`-annotated
  method (both in `inquisitor-harness-junit`). `@Harness` carries an
  `@ExtendWith(HarnessExtension.class)`; `@Scenario` is a meta-`@TestTemplate`
  with `@ExtendWith(ScenarioTemplateProvider.class)`. Building on standard
  Jupiter mechanisms keeps `SpringExtension`/`@SpringBootTest` working out of the
  box — a custom engine would have to bootstrap Spring itself. Inheriting a base
  class was rejected: it burns the single-inheritance slot and is less idiomatic
  than extensions (same reasoning as the controller tracing aspect).
- **One `@Scenario` method = one scenario; one sub-test per step.** The
  `ScenarioTemplateProvider` parses the scenario and returns one
  `TestTemplateInvocationContext` per `## Step`, so the method is the per-scenario
  container and the steps nest under it like a parameterized test's invocations.
  All steps share one `ScenarioEvaluation` (an id from step 1 flows on via chat
  memory); they execute sequentially, each advancing the same conversation.
- **Fail-fast via skip.** A `BeforeTestExecutionCallback` runs each step before
  its (empty) test body; the first failing step throws an `AssertionError`, and
  the remaining steps are reported **skipped** (`TestAbortedException`) so the
  report still lists them rather than dropping them.
- **Scenario file resolved from the method name.** `@Scenario` with no value
  derives `<methodName-kebab>.md` under the class's `@Harness#scenarioDir()`
  (default `classpath:scenarios/`), e.g. `transferBetweenAccounts()` →
  `transfer-between-accounts.md`; an explicit `@Scenario("classpath:…")` overrides.
- **App HTTP target auto-registered from `local.server.port`.** `HarnessExtension`
  (a `BeforeEachCallback`) registers `HarnessDefaults.APPLICATION` from the random
  server port, removing the per-suite `@BeforeEach` the standalone `ScenarioTests`
  needs. When there is no web server (a SQL-only suite) it skips registration.
- **Annotations live in `…-junit`, not `…-junit-starter`.** They need JUnit + the
  extensions on the classpath; the starter just re-exports `…-junit` plus
  `…-harness-starter` so a consumer's single test dependency brings the
  annotations, extensions, and harness autoconfig.
- **Standalone `ScenarioTests` kept alongside the JUnit `ScenarioSuiteTest`.** The
  demo exercises both paths: `ScenarioTests` asserts on `ScenarioResult` via the
  JUnit-free contract (compatibility), `ScenarioSuiteTest` uses the ergonomic
  `@Harness`/`@Scenario` layer. Both are gated on `INQUISITOR_LLM_IT`.

> Conventions for code style live in the `java-developer` skill, not here. This
> file records project-specific decisions only.
