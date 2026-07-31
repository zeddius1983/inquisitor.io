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
- **Model-dependent beans degrade gracefully.** `ChatClient`/`LlmStepRunner`
  beans are `@ConditionalOnBean(ChatModel.class)` and the autoconfig is ordered
  `afterName` the OpenAI chat autoconfig, so the context still starts when no
  model is configured. The semantic loggers and model registry are always available,
  and a user-supplied
  `StepRunner` can still obtain a `ScenarioExecutor` without a `ChatModel`.
- **Harness logging is semantic and presentation-selectable.**
  `inquisitor.harness.logging.format=plain|markdown` selects implementations of
  `LlmStepRunnerCallback`, `EvaluationStepRunnerCallback`, and
  `ScenarioExecutionCallback`; the shipped loggers implement these generic
  lifecycle seams while runners remain presentation-agnostic. Step-runner callbacks
  compose sequentially through `andThen`; `StepEvaluationRecorder` implements the
  evaluation seam and is chained before the narrower `EvaluationLoggerCallback`.
  Scenario lifecycle
  narration is centralized in `ScenarioExecution`; actor step narration is emitted
  once by `LlmStepRunnerCallback.stepStarted`, together with its running status. This preserves
  whole-run and JUnit step-at-a-time behavior without duplicate step messages.
  Markdown events use the `INQUISITOR_MARKDOWN` SLF4J marker; raw, manually
  rendered, and automatically rendered consoles remain consumer choices.
- **Actual model metadata is observed, never probed.** A response advisor on each
  real actor/judge `ChatClient` call stores the first nonblank server-reported
  model in a shared `ModelRegistry`; no configured options or endpoints are copied
  into logging state. Configuration-time probe calls would add cost, side effects,
  and could report a route different from the scenario request.
  The Markdown actor logger reads the registry directly and puts the actual actor
  name first in step breadcrumbs once known. Provider-reported file paths are shown
  as the filename without `.gguf`. Before the first usable response, the model
  segment is omitted; there is no model preamble or unresolved placeholder.
  Credentials, prompts, tool arguments, and default headers are excluded.
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
  by `ScenarioSuite` subclasses. Both are gated with `@RequiresLlm`.
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

- **Scenarios split by authoring style:** `scenarios/{explicit,cucumber,intent}`. The
  same scenarios are written in different engineering voices — `explicit` (prescriptive:
  fenced requests + bulleted asserts), `cucumber` (Gherkin Given/When/Then), and
  `intent` (pure natural-language intent, no endpoints) — so we can measure how a
  model copes with each. The ergonomic suite is an abstract `ScenarioSuite`
  bound to a bucket by each subclass (`ExplicitScenarioSuiteTest`,
  `CucumberScenarioSuiteTest`); `intent` runs via `IntentScenarioSuiteTest`, which
  needs OpenAPI discovery.
- **No `positive/`/`negative/` split — fault scenarios reuse the positive ones.** The
  tree was originally `positive/*` vs a reserved `negative/*`. Once oracle calibration
  moved to *mutation testing* (correct scenarios run against a buggy build — see *Fault
  detection* below), a "negative" scenario became byte-identical to a positive one, so a
  separate folder was pure duplication. The tree was flattened to the style buckets and
  `negative/` removed.

## Fault detection (oracle calibration)

- **Mutation testing, not false-expectation fixtures.** To measure that the oracle
  catches wrong answers (*specificity*), we run *correct* scenarios against a
  deliberately buggy build and treat a `FAIL` as the success condition — rather than
  feeding the model a self-contradictory scenario (deposit 100, assert 50). A false
  expectation puts the anomaly in the *instructions*, which an instruction-following
  model can smell and "helpfully" rationalise (a PASS is then ambiguous); a seeded bug
  puts the anomaly in the *system* — the real situation an oracle faces (good tests,
  buggy code) — leaving no escape hatch.
- **A runtime fault router, not Spring profiles.** `AccountServiceRouter` (`@Primary`)
  routes each call to the clean `AccountServiceImpl` or the defective
  `BuggyAccountServiceImpl` by which `Bug` is enabled (`enableBug`/`disableBug`/
  `disableAllBugs`) — a strategy switch behind a facade. Profiles would cost a fresh
  Spring context per mutant set and can't switch mid-suite; the router lives in one
  context and flips per test. It's `@Primary` and always present (no
  `@TestConfiguration`/`@Import`); with no bug enabled it routes to clean, so `bootRun`
  and the positive suites behave identically.
- **`BuggyAccountServiceImpl implements AccountService` — it does not extend the clean
  impl.** Extending it would make it an `AccountServiceImpl` too, so the router's
  `@Autowired AccountServiceImpl` would be ambiguous; instead it delegates every
  non-buggy operation to the clean bean and overrides only the defective ones.
- **Two suites, coarse-vs-precise.** `FaultDetectionTests` (Phase 1, standalone) enables a
  bug, runs an existing positive scenario, and asserts the failure lands at the *exact*
  step the bug manifests at. `FaultDetectionSuiteTest` (Phase 2) brings fault detection to
  the ergonomic `@Harness` layer; it asserts only that the scenario fails *somewhere*, the
  natural granularity of a per-step `@TestTemplate`. Both stay — precise-but-manual beside
  ergonomic-but-coarse. See `tasks/task-07-fault-detection.md`.
- **`@Scenario(expect = FAIL)` inverts the template, not the oracle.** The Phase 2
  ergonomic path needs the `@Scenario` template — which normally asserts every step
  *passes* — to instead treat a failing step as success. Rather than a second template or a
  bespoke fault annotation, an `Expect { PASS, FAIL }` enum + `expect()` attribute flips
  the `ScenarioTemplateProvider`'s verdict handling: in `FAIL` mode a failing step is the
  green success (rest skipped) and an all-pass run fails the test. `PASS` is the default, so
  every existing suite is untouched, and the oracle itself is unchanged — only the harness's
  interpretation of its verdict differs.
- **`@EnableBug` carries its own extension; the toggle brackets each step.** Bug selection
  is a demo concern, so `@EnableBug(Bug)` lives in the demo's `src/test` and meta-annotates
  `@ExtendWith(BugInjectionExtension.class)` — annotating a method is all it takes, no
  class-level wiring. Because a `@Scenario` is a `@TestTemplate`, the extension's
  before/after-each fire per step invocation, so it (re-)enables the bug before every step
  and clears it after: active for the whole scenario, reset once done.

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

## Evaluation report (task-08 C2)

- **File handoff, the JaCoCo pattern.** The `evaluate` task is a Gradle `Test` task,
  so the evaluation data lives in the forked test JVM and Gradle sees only test
  outcomes — there is no channel back to the build but files. The test JVM writes
  `evaluation.json` + `evaluation.md`; the plugin's `evaluateReport` task only echoes
  the Markdown headline (no parsing, no Jackson in the plugin). Rendering lives next
  to the data.
- **One flush point: a JUnit `LauncherSessionListener`** (ServiceLoader-registered in
  `inquisitor-harness-evaluation`), firing once after the whole test plan — pass or
  fail. Recorder beans live in cached Spring contexts the listener can't see, so the
  starter registers each recorder in a static `EvaluationReportSession` the listener
  drains. Deliberately the only static state in the module; the alternative
  (`@PreDestroy` flush per context) runs in JVM-shutdown-hook ordering and needs
  read-modify-write file merging. The listener no-ops unless `inquisitor.report.dir`
  is set (only the plugin's `evaluate` task sets it), so ordinary test runs write
  nothing even with evaluation enabled.
- **The echo lives inside `evaluate` (root-suite `afterSuite`), not a finalizer
  task.** Reporting should be implicit, like any `Test` task's HTML report — a
  separate `evaluateReport` task was surface without substance (it never rendered
  anything; rendering lives in the test JVM). A failing scenario run is exactly when
  the report matters and `doLast` never runs when the test action throws — but
  `TestListener.afterSuite` on the root descriptor fires after all tests *before*
  the task fails, so the echo survives a red run without a second task.
- **The judge is an observer — its failures never fail the step.** The second real
  run had a judge call hang ~4 minutes and throw `InterruptedIOException`, which
  failed the JUnit sub-test and desynced the sub-test↔step mapping (the execution
  cursor hadn't advanced, so the next sub-test silently re-ran the step under the
  wrong name and the last step never ran). Two fixes: `EvaluationStepRunner`
  catches judge `RuntimeException`s and records `NOT_EVALUATED` with the error as
  feedback (the verdict stands untouched — decorator transparency is the contract);
  and the JUnit layer's `StepExecution` marks the scenario done when `next()`
  throws, so a genuine infrastructure failure aborts cleanly instead of shifting
  steps.
- **Synthetic verdicts are not audited.** When the actor's response is empty or
  unparseable, `LlmStepRunner` fabricates the FAIL (now marked `StepRun.synthetic`);
  the first real run showed the judge "contradicting" such a verdict — technically
  right, semantically noise, since there is no actor claim to audit. The evaluation
  runner skips the judge and records `NOT_EVALUATED`, excluded from the mean score
  but counted in the report (it is a run-health signal).
- **The report's PASSED/FAILED is expectation-aware — JUnit's reading.** A scenario
  is *PASSED* when its outcome matches `@Scenario(expect)`: an expected failure that
  failed is PASSED; one that stayed green is *FAILED (missed detection)*. Success
  rate aggregates passed scenarios (so a clean fault-suite run reads 100%, matching
  the green JUnit report), while the raw actor verdicts stay visible per step on the
  scenario pages. (Originally shipped as a separate "Matched" column beside a
  step-based success rate; review showed that misleads — the JUnit reading is the
  right primary status.)
- **The deterministic gate is "outcome matches expectation", not "all PASS".**
  Fault-detection suites (`@Scenario(expect = FAIL)`) are part of `evaluate` runs and
  a detected fault is a success; a fully-green fault run is a *missed detection*. The
  expectation travels on the core model (`Scenario.expectedOutcome`, default PASS,
  set by the JUnit layer) because the evaluation module can't see JUnit annotations.
  This is also the groundwork for task-07's deferred detection-%.
- **Reporting is its own module (`inquisitor-harness-evaluation-report`).** Keeps the
  judge module purely recorder + judge (the launcher and Jackson dependencies move
  out), isolates the one piece of static state (`EvaluationReportSession`) and the
  ServiceLoader listener, and gives renderer growth (C3: HTML, templates,
  multi-config aggregation) a home. The starter ships it by default
  (`implementation` dependency) with the registration bean
  `@ConditionalOnClass`-guarded, so excluding the module degrades evaluation to
  score-only — the OpenAPI-grade removal bar.
- **Renderers are pluggable via `ServiceLoader`, selected by `--report`.**
  `EvaluationReportRenderer` (`name()` + `render(report, dir) → List<Path>`) is the
  format seam — a renderer owns its files, because the HTML report is multi-page
  (Gradle-report-style: index → bucket pages → scenario pages). The module
  contributes `html` (default), `markdown` and `json` through its own
  `META-INF/services` entry; any jar on the test classpath can contribute more, and
  the `evaluate` task's `--report=name,name` option (a `@Option` on the custom
  `EvaluateTask`, passed as `inquisitor.report.formats`) selects by `name()` —
  built-ins and user renderers alike; unknown names are warned and skipped.
  `ServiceLoader` over Spring beans because the writing happens in the
  `LauncherSessionListener`, outside any context.
  Rendering by `StringBuilder`, not a template engine, for now: Markdown is
  newline-sensitive and the document is ~100 lines of logic; a logic-less engine
  (JMustache — not Groovy, which drags in a whole language runtime as a consumer-test
  dependency) becomes attractive if C3's formats grow, and would slot in behind the
  renderer interface.
- **Report grouping: `Scenario.group` (the suite), source directory as fallback.**
  Originally the report grouped by the parent directory of `Scenario.source` (the
  style bucket), because the suite class name never reaches the evaluation layer
  (dependency arrow: junit → harness → evaluation seam). Review showed source is the
  wrong denominator — a suite may mix scenarios from any buckets, and the same file
  runs under several suites with different expectations. So the caller now declares
  the run context on the model: `Scenario.group`, set by the JUnit layer to the suite
  class's simple name (`FaultDetectionSuiteTest`), optional for standalone runs, where
  the report falls back to the source directory. The JUnit provider passes the full
  location (not the bare filename) as `source`. Records are split into scenario
  *instances* by group/source changes and step-index resets, because the same file
  legitimately runs several times in one JVM.

## Console Markdown logging

- **Reusable Logback integration is a separate opt-in module.**
  `inquisitor-logback-markdown` owns the `%mdMsg` converter, Flexmark renderer,
  marker protocol, and bundled conversion rule without depending on Spring or
  the harness. Consumers choose which console layout uses it; file and structured
  appenders remain on `%msg`. This keeps presentation out of the harness and
  makes the renderer reusable by unrelated applications.
- **Flexmark core, not `flexmark-all`.** The initial renderer needs the parser,
  core AST, and AST utilities only. Pulling every Flexmark extension and converter
  into a published logging utility would inflate every consumer's runtime
  classpath. Individual extensions, such as tables in task 15D, are added
  narrowly when their behavior is implemented.
- **Table layout is measured before ANSI emission.** The narrow Flexmark tables
  extension supplies structural rows/cells/alignment; those nodes are collected
  into an immutable internal model before a dedicated terminal renderer measures,
  allocates, wraps, and emits them. Display width ignores ANSI and combining marks
  and accounts for common wide Unicode/emoji. This keeps colour independent from
  geometry and per-render mutable state isolated for concurrent logging.
- **Jansi emits styles but does not decide whether a destination is a terminal.**
  Automatic mode requires an attached `System.console()`, honours `NO_COLOR`
  and `TERM=dumb`, and respects Jansi's process disable property. Manual
  `plain`/`ansi` converter options provide deterministic overrides. Spring
  Boot's ANSI policy is separate and is bridged by the optional starter, rather
  than coupled into this Spring-free module.
- **Automatic installation mutates only compatible console layout instances.**
  The starter waits until all singletons exist, discovers identity-deduplicated
  console appenders through logger attachments and `AppenderAttachable`
  composites, and updates each `PatternLayout#getInstanceConverterMap()` under
  the `LoggerContext` configuration lock. It supplies a marker-only converter
  for `m`, `msg`, and `message`; normal messages remain raw and file/JSON/custom
  layouts are untouched. Dynamically created `SiftingAppender` children require
  manual `%mdMsg{marked}` configuration because they do not exist during starter
  installation and are not exposed as attached appenders. Global converter maps
  and consumer logging configuration files remain owned by the application. The
  Logback configuration lock serializes configuration changes, not active event
  formatting, so this mutation is deliberately startup-only; applications should
  not invoke the installer later while background threads are logging.
- **No Lombok in the small renderer module.** Its state is deliberately explicit
  and per-render-call, and the handful of constructors/accessors do not justify
  adding an annotation processor to this dependency-light published artifact.
- **Code highlighting is bounded, pluggable, and source-preserving.** ANSI code
  blocks use equal-width background panels and a small built-in tokenizer for the
  harness's common JSON/SQL/HTTP/Java/shell fences. `SyntaxHighlighter` remains a
  public seam for other grammars. Highlighting is skipped for large blocks, and
  output that does not reconstruct the exact source line falls back to the base
  code style; a debugging renderer must never mutate logged payloads.
- **Powerline breadcrumbs are a renderer convention, not embedded ANSI.** A
  heading shaped as ` segment  segment … ` renders as background-colored
  Powerlevel10k-style pills. The classic baseline Powerline caps are preferred
  over rounded extra-symbol glyphs for broader patched-font support. The source
  remains readable Markdown, plain output remains escape-free, and the harness
  stays independent of Logback/Jansi.

> Conventions for code style live in the `java-developer` skill, not here. This
> file records project-specific decisions only.
