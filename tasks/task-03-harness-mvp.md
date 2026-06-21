# Task 03 — Harness MVP

Implement the core Inquisitor harness: an LLM-driven scenario executor that uses
a Spring AI `ChatClient` with **tools** to drive the application under test, and
returns a structured **verdict** per step. JUnit surfaces each step as its own
test.

> Status: **plan / draft — amend before implementation.**
> Design choices flagged with **[DECIDE]** are defaults I picked; change freely.

## Goals

- Parse multi-step markdown scenarios (`## Step N - …`).
- Execute each step via `ChatClient`, sharing one conversation per scenario so
  state (e.g. an id created in step 1) flows to later steps via chat memory —
  no manual id extraction.
- Tools let the LLM make **HTTP** and **SQL** calls; the LLM decides which to
  invoke. We only collect its structured verdict.
- **Fail-fast within a scenario**; scenarios are independent of each other.
- JUnit shows **one test per step** (a scenario = a container of step-tests).

## Non-goals (MVP)

- The `inquisitor-mock` server (reserved; a mock just becomes a named HTTP
  target later).
- Parallel scenario execution, retries, flake mitigation beyond temperature 0.
- Assertion DSL / deterministic variable capture — the LLM does the work.

---

## Design decisions

### Execution model — Option B (step-by-step, shared memory)

- One `ChatClient` call per step; all steps in a scenario share a
  `conversationId` backed by `ChatMemory` (`MessageChatMemoryAdvisor`).
- Prior steps' messages **and tool-call results** stay in context, so the model
  already knows the id it created earlier. No templating, no regex.
- A scenario with no `## Step` headers collapses to a single implicit step
  (this is the degenerate "one-shot" mode, for free).

### Verdict — structured output, not free text

```java
enum Outcome { PASS, FAIL }
record StepVerdict(Outcome outcome, String reasoning, List<String> evidence) {}
```

- Obtained via `ChatClient … .entity(StepVerdict.class)`.
- System prompt instructs the model to perform the step using tools and cite the
  tool responses it relied on in `evidence`.
- Step asserts `outcome == PASS`. First FAIL aborts the rest of the scenario.

### Tools — named target/datasource registries (no default)

Registry = allow-list: the LLM can only reach configured targets/datasources.
There is **no privileged "primary"**. Resolution when a tool omits the name:
**exactly one registered → use it; several → must name one; none → error.**
So single-target scenarios never name a target; multi-target ones always do.

HTTP tool:
```
httpRequest(target?, method, path, body?)
```
- The app under test is registered (base URL from `@LocalServerPort`); as the
  sole target it's used when `target` is omitted.
- Extra targets via config; once >1 exists, every call must name its target.

SQL tool:
```
sqlQuery(datasource?, sql)     // read AND write
```
- The app's `DataSource` is registered; sole datasource used when omitted.
- Extra datasources via config (external / mock / per-purpose DBs).

**User-supplied tools (near-term requirement).** The built-in `httpRequest` /
`sqlQuery` are not the only tools — a consumer must be able to add their own. The
executor/starter aggregate the built-ins with any user-provided tools found in
the Spring context:
- tool objects (beans with `@Tool` methods) → passed via `ChatClient.tools(...)`;
- `ToolCallback` beans (incl. MCP-provided ones from
  `SyncMcpToolCallbackProvider`) → passed via `ChatClient.toolCallbacks(...)`.

So the executor's tool input is "built-ins + everything the consumer registered",
and the seam stays source-agnostic.

Config shape:
```yaml
inquisitor:
  harness:
    model:
      temperature: 0.0          # determinism
    http:
      allow-unlisted: false     # [DECIDE] escape hatch for arbitrary URLs
      targets:
        inventory-mock:
          base-url: http://localhost:9090
          default-headers: { }
    datasources:
      reporting:
        url: jdbc:postgresql://…
        username: …
        password: …
```
(The app target/datasource is registered automatically by the starter; only
extras are listed here.)

---

## Module / package layout

All in `inquisitor-harness` unless noted. Base package `io.inquisitor.harness`.

```
io.inquisitor.harness
├── model/        Scenario, Step, Outcome, StepVerdict, StepResult, ScenarioResult
├── parser/       ScenarioParser (flexmark)
├── tool/         HttpRequestTool, SqlTool, HttpTargetRegistry, DataSourceRegistry
├── executor/     ScenarioExecutor, SystemPrompt
└── (root)        InquisitorHarnessProperties
```

### Model (`model/`)

- `Scenario(String name, String description, List<Step> steps, @Nullable Path source)`
- `Step(int index, String title, String instruction)`
- `enum Outcome { PASS, FAIL }`
- `StepVerdict(Outcome outcome, String reasoning, List<String> evidence)`
- `StepResult(Step step, StepVerdict verdict, Duration elapsed)`
- `ScenarioResult(Scenario scenario, List<StepResult> results)` — `passed()` helper.

### Parser (`parser/`)

- `ScenarioParser` (flexmark). H1 `#` → scenario name; text before the first
  `## ` → `description` (shared context for every step); each `## ` section →
  a `Step` (title + body instruction).
- No `## ` headers → single implicit step whose instruction is the whole body.

### Tools (`tool/`)

- `NamedRegistry<T>` — shared allow-list base: `register(name, entry)` + `resolve(name?)`
  (explicit name; or sole entry when omitted; else error). Subclassed by:
- `HttpTargetRegistry` — name → `HttpTarget` (base URL + default headers).
- `DataSourceRegistry` — name → `DataSource`.
- `HttpRequestTool` — `@Tool httpRequest(...)` using `RestClient`; returns status
  + headers + body as a compact JSON-ish string the model can read.
- `SqlTool` — `@Tool sqlQuery(...)` via `JdbcTemplate`; SELECT → rows as
  `List<Map>`; DML → update count. Read **and** write.

### Executor (`executor/`)

- `ScenarioExecutor.evaluate(Scenario) : ScenarioResult` — convenience:
  evaluates all steps, fail-fast. Used by Mode A's `@TestFactory`.
- Step-at-a-time API (used by Mode B's `@TestTemplate`, and internally by
  `evaluate`): `ScenarioEvaluation start(Scenario)` → `StepResult next()` /
  `boolean hasNext()`, holding the `conversationId` so the chat memory persists
  across steps. Both modes therefore drive the same path.
  - new `conversationId` per evaluation; `ChatClient` request with the tool
    beans + `MessageChatMemoryAdvisor` + system prompt + temperature 0.
  - per step: prompt = step instruction (+ scenario description on step 1);
    `.entity(StepVerdict.class)`; record `StepResult`; **stop on FAIL**.
- `SystemPrompt` — role text: "you execute one integration-test step; use the
  provided tools; respond with a verdict and cite tool responses as evidence."

### Properties (root)

- `InquisitorHarnessProperties` (`inquisitor.harness.*`): model options, http
  targets + allow-unlisted, datasources.

---

## Autoconfiguration — `inquisitor-harness-starter`

`@AutoConfiguration` providing, when missing:
- `ChatClient` from the autoconfigured `ChatModel`
  (`spring-ai-starter-model-openai`, already a dep of `inquisitor-harness`).
- `ChatMemory` (in-memory window).
- `HttpTargetRegistry`, `DataSourceRegistry` (register the app's target/datasource
  from context: base URL from `@LocalServerPort`, `DataSource` bean).
- `HttpRequestTool`, `SqlTool`.
- `ScenarioExecutor` — built with the built-in tools **plus** every user-supplied
  tool object (`@Tool` beans) and `ToolCallback` bean (incl. MCP) collected from
  the context.
- `@EnableConfigurationProperties(InquisitorHarnessProperties.class)`.

---

## Standalone usage (no JUnit)

The core `inquisitor-harness` module has **no JUnit dependency**. A user can run
scenarios programmatically and handle the verdicts themselves:

```java
val scenario = scenarioParser.parse(markdown, "my-scenario.md");
val result   = scenarioExecutor.evaluate(scenario);   // ScenarioResult
result.results().forEach(r ->
    System.out.println(r.step().title() + " -> " + r.verdict().outcome()));
```

`ScenarioResult` / `StepVerdict` are the public contract; JUnit is just one
consumer of them. **Keep `…-harness` free of JUnit** so this stays true.

---

## JUnit integration — `inquisitor-harness-junit` (+ starter)

No custom JUnit `TestEngine` — everything builds on standard JUnit 5 mechanisms
so `SpringExtension`/`@SpringBootTest` work out of the box (a custom engine would
have to bootstrap Spring itself). Consumers `extends InquisitorScenarioTests` for
the autowiring + shared setup.

Two usage modes, both reporting **one test per step** and fail-fast per scenario:

### Mode A — class-level directory discovery (`@TestFactory`)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@InquisitorTest(scenarioDirs = "classpath:scenarios/")
class ScenarioSuiteTest extends InquisitorScenarioTests {}
```

- The base class's `@TestFactory` reads `@InquisitorTest(scenarioDirs)` off the
  runtime class, discovers + parses every `*.md`, and emits a `DynamicContainer`
  per scenario containing one `DynamicTest` per step.

### Mode B — method-level single scenario (`@TestTemplate`)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ScenarioReadOnlySuiteTest extends InquisitorScenarioTests {

    @InquisitorTest(scenario = "classpath:scenarios/account-not-found.md")
    void somethingNotFound() {}

    @InquisitorTest(scenario = "classpath:scenarios/transfer-between-accounts.md")
    void transfer() {}
}
```

- `@InquisitorTest` is **meta-annotated** with `@TestTemplate` +
  `@ExtendWith(InquisitorStepTemplateProvider.class)`. On a method it behaves as a
  test template: the provider parses the single `scenario` file and returns one
  `TestTemplateInvocationContext` per step (display name = step title).
- The method body stays empty; each invocation's context carries an extension
  that executes that step against the shared conversation and asserts `PASS`.
- The per-scenario conversation/`conversationId` + the running `ScenarioResult`
  live in the `ExtensionContext` store at the **template-method** level, so all
  step invocations of one method share state (this is what threads the id across
  steps). Fail-fast: once a step fails, later invocations `abort` (skip).

### Shared base class

`InquisitorScenarioTests` (in `…-junit`):
- autowires `ScenarioExecutor`; resolves the port (`local.server.port`) and
  registers the app's HTTP target (from the local server port) before tests run;
- hosts the Mode-A `@TestFactory` (inert when the class has no `scenarioDirs`).

### Annotation

```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate                                          // active only on methods
@ExtendWith(InquisitorStepTemplateProvider.class)
public @interface InquisitorTest {
    String[] scenarioDirs() default {};   // Mode A — class-level discovery
    String scenario() default "";          // Mode B — method-level single file
}
```

(`@TestTemplate` on a TYPE is ignored by JUnit, so the dual targeting is safe.)
`…-junit-starter`: autoconfig (if any) + re-exports the `@InquisitorTest`
annotation.

> Note: the executor exposes a step-at-a-time entry point (start a scenario →
> run next step over the shared conversation) so both the `@TestFactory` and the
> `@TestTemplate` provider drive the same execution path.

---

## Wire-up & verification (`inquisitor-demo`)

- `ScenarioSuiteTest` extends `InquisitorScenarioTests` (Mode A); the existing 5
  scenarios in `src/test/resources/scenarios/` become the acceptance suite. Add a
  small Mode-B class to exercise method-level annotations too.
- Model: **local llama.cpp, OpenAI-compatible API at `http://127.0.0.1:8000`**.
  Config in test `application.yml`:
  ```yaml
  spring:
    ai:
      openai:
        base-url: http://127.0.0.1:8000   # client appends /v1
        api-key: llama
        chat:
          options:
            model: gemma-4-31B-it-QAT-Q4_0
            temperature: 0.0
  ```
- `./gradlew :inquisitor-demo:test` runs the scenarios; each step shows as a
  test. The local model must be running for the suite to pass.

---

## Phasing

1. **Model + parser** (+ unit tests on the 5 demo scenarios' structure). ✅ done
2. **Executor** (ChatClient + memory + structured verdict), built against a
   `StepEvaluator` seam with **stub/mock tools** — defer real tool impls. ✅ done
   - `ScenarioExecutor` orchestration unit-tested with a *fake* `StepEvaluator`
     (deterministic, no model; runs in normal `build`).
   - `ChatClientStepEvaluator` (real LLM) exercised by a **gated** integration
     test using stub `@Tool` beans + the local model.
3. **Tools + registries** (real HTTP/SQL; HTTP tested via a JDK `HttpServer`,
   SQL via a Postgres Testcontainer). ✅ done
4. **Starter** autoconfiguration (incl. aggregating user-supplied tools).
5. **JUnit `@TestFactory`** + wire into demo; run the 5 scenarios green.

> Rationale for 2-before-3: lets us debug the executor + validate the local
> model's tool-calling/structured-output reliability with canned tool responses,
> independent of real HTTP/SQL plumbing.

## Future (Phase 6+)

Not in the MVP; captured so the design leaves room for them:

- **Typed tools derived from the app's API.** Generate precise, per-endpoint
  tool definitions from the controllers' request mappings / OpenAPI spec so the
  LLM sees exact typed operations — but keep execution over **real HTTP**
  (delegating to the `httpRequest` path) to preserve integration-test fidelity.
  (Calling controller/repository methods directly as tools is possible via
  `MethodToolCallback`, but bypasses the HTTP stack — status, validation,
  serialization, security — so it's rejected for action steps.)
- **Consume MCP tools.** If the app under test exposes beans as MCP tools, let
  the harness act as an MCP client (`spring-ai-starter-mcp-client` →
  `SyncMcpToolCallbackProvider`) and feed those callbacks in. Mostly falls out of
  the "user-supplied `ToolCallback` beans" support from phase 4.
- **`inquisitor-mock` server** as another named HTTP target.

## Resolved decisions

- [x] Named-registry (allow-list) tool design, **no default/`primary`**: omitted
      name resolves only when exactly one entry exists, else the call must name one.
- [x] `http.allow-unlisted` escape hatch — **omit for MVP** (allowlist only; can
      add later without redesign).
- [x] JUnit: base class + standard mechanisms — `@TestFactory` (Mode A, class +
      `scenarioDirs`) and `@TestTemplate` (Mode B, method + `scenario`). No
      custom engine.
- [x] Model endpoint: local llama.cpp at `http://127.0.0.1:8000`.

## Still open (non-blocking)

- [ ] `@InquisitorTest` needs `@TestTemplate`/`@ExtendWith` meta-annotations →
      it must move to (or pull JUnit deps into) the module that has JUnit on the
      classpath. Land it in `…-junit` rather than `…-junit-starter`? (was cosmetic,
      now slightly load-bearing).
- [x] Local model: `gemma-4-31B-it-QAT-Q4_0` (**dense**). MoE models (e.g.
      `gemma-4-26B-A4B`) shortcut multi-step scenarios — they answer follow-up
      steps from chat memory instead of calling the tool, producing hallucinated
      PASSes. A dense model reliably calls a tool on every step.
