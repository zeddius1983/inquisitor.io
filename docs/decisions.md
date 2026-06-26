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
- **Controller + repository tracing via one AOP aspect.** `TracingAspect`
  (`io.inquisitor.demo.aop.log`, `spring-boot-starter-aspectj`) carries two
  pointcuts — every `@RestController` call and every Spring Data repository call —
  both delegating to a shared advice that logs `method(args) -> result`/`-> threw …`.
  Together they show the LLM-driven HTTP request *and* the persistence calls behind
  it, confirming the harness drives *real* traffic rather than fabricating tool
  results; aspects (vs per-method logging) keep controllers/repositories clean and
  also trace exception paths. The repository pointcut matches the Spring Data
  `Repository` marker via `target(...)` — a per-interface `this(...)` pointcut
  matched the inherited `CrudRepository` methods (`save`, `findById`) unreliably.
  Tracing is logged at **debug** level and the advice short-circuits to `proceed()`
  when the resolved category has debug disabled, so it adds no overhead in normal runs.

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
- **Standalone `ScenarioTests` kept alongside the JUnit suites.** The demo
  exercises both paths: `ScenarioTests` asserts on `ScenarioResult` via the
  JUnit-free contract (compatibility), while the `@Harness`/`@Scenario` layer is run
  by `PositiveScenarioSuite` subclasses. Both are gated with `@RequiresLlm`.
- **LLM gating is a separate `@RequiresLlm`, not folded into `@Harness`.** `@Harness`
  stays a pure capability marker; gating is an opt-in companion annotation. Baking the
  gate into `@Harness` would couple the published API to one CI convention and, worse,
  **silently skip** any consumer's tests merely for adding `@Harness` — a bad default.
  Kept as its own annotation, it also covers the non-`@Harness` `ScenarioTests`.
- **`@RequiresLlm` is `@Inherited`; a plain `@EnabledIfEnvironmentVariable` is not.**
  JUnit's condition lookup (`AnnotationSupport.findAnnotation`) does **not** walk to a
  superclass for a non-`@Inherited` annotation, so a gate declared on an abstract base
  would not gate its subclasses — yet Spring **does** inherit `@SpringBootTest`, so the
  subclasses still bootstrap and run. That asymmetry leaked the gated suites into CI
  once. Marking `@RequiresLlm` `@Inherited` restores "declare once on the base".
- **Env var first, then a JUnit config parameter — not a Spring property.** An
  `ExecutionCondition` runs **before** the `TestContext` exists, so it cannot read
  `application.yml`. Resolution is `INQUISITOR_LLM_IT` (authoritative when set) →
  `inquisitor.harness.llm.enabled` JUnit configuration parameter (from a `-D` system
  property or `junit-platform.properties`, the file-based equivalent available that
  early) → disabled by default. The pure decision is split into
  `RequiresLlmCondition.resolve(env, property)` so it is unit-testable without touching
  the ambient environment.

## Scenario layout & authoring styles

- **Scenarios split by purpose then authoring style:**
  `positive/{explicit,cucumber,intent}` and `negative/{explicit,cucumber}`. The same
  scenarios are written in different engineering voices — `explicit` (prescriptive:
  fenced requests + bulleted asserts), `cucumber` (Gherkin Given/When/Then), and
  `intent` (pure natural-language intent, no endpoints) — so we can measure how a
  model copes with each. The ergonomic suite is an abstract `PositiveScenarioSuite`
  bound to a bucket by each subclass (`ExplicitScenarioSuiteTest`,
  `CucumberScenarioSuiteTest`); `intent` runs via `IntentScenarioSuiteTest`, which
  needs OpenAPI discovery. `negative/*` is reserved for oracle-calibration fixtures.

## OpenAPI discovery (optional plugin)

- **A separate, removable module, not a core feature.** `inquisitor-harness-openapi`
  (+ `-starter`) is an opt-in plugin; deleting it leaves the core untouched. It earns
  its keep only for the `intent` style, so it must not entangle the executor.
- **Delivered as a Spring AI `Advisor`, not a custom prompt SPI.** `OpenApiAdvisor`
  implements `BaseAdvisor` and augments the request's system message in `before(...)`
  — the same mechanism RAG's `QuestionAnswerAdvisor` uses. The only core change is
  that the ChatClient autoconfig now **collects `Advisor` beans** (like it already
  collects `ToolCallback`s); with no advisor beans, behaviour is identical to before.
  This is the whole seam — generic, not OpenAPI-aware.
- **`augmentSystemMessage(String)` *replaces*, so we use the function overload.** The
  String overload of `Prompt.augmentSystemMessage` overwrites the system text; to
  *append* the spec to the harness base prompt we pass a `Function<SystemMessage,…>`
  that concatenates. (Verified against Spring AI 2.0.0-RC1 sources.)
- **Live-fetch, lazily, from the registered app target.** `HttpOpenApiSpecProvider`
  reads the app base URL from `HttpTargetRegistry` and fetches `/v3/api-docs.yaml` on
  first use (by then the server is up and the target registered), then caches — so no
  JUnit-layer startup hook is needed. A static `location` selects
  `ResourceOpenApiSpecProvider` instead.
- **Fail fast, not silent degrade.** Because `enabled=true` is an explicit opt-in,
  an unobtainable spec throws (naming the location) rather than running without it —
  silently omitting the spec would mislead the user into thinking it reached the model.
- **`@EnableOpenApiDiscovery` is sugar over the property, not a parallel mechanism.**
  The annotation maps to `inquisitor.harness.openapi.enabled` via a Spring
  `ContextCustomizerFactory` (registered in `spring.factories`, the same approach as
  Boot's `@AutoConfigure…` slices) — so there's one source of truth and the test
  surface stays declarative without touching `@Harness`. It lives in the plugin
  module, so removing the plugin removes the annotation too. (`@ApiSpec`, for
  pointing at a specific spec, remains a possible later addition.)
- **YAML, raw.** The spec rides in the system prompt and is re-sent every round-trip
  (chat memory), so YAML's smaller token footprint compounds; a `$ref`-resolving
  digest is a later, size-gated optimisation.
- **The demo serves a static `openapi.yaml` at `/v3/api-docs.yaml`** rather than
  taking on the springdoc-on-Boot-4 dependency risk. This still exercises the
  headline live-fetch path deterministically; real consumers plug in springdoc.

> Conventions for code style live in the `java-developer` skill, not here. This
> file records project-specific decisions only.
