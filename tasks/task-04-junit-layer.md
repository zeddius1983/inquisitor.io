# Task 04 — `@Harness`/`@Scenario` JUnit layer

The ergonomic JUnit 5 surface on top of the proven standalone harness: annotate a
`@SpringBootTest` class with `@Harness` and write one `@Scenario` method per
scenario. Each scenario's `## Step`s are reported as **sub-tests** (like a
parameterized test's invocations), fail-fast, with no manual target registration.

This realises the `inquisitor-harness-junit` + `inquisitor-harness-junit-starter`
modules (previously empty scaffolding). It is the deferred JUnit section of
[task-03](task-03-harness-mvp.md).

> Status: **implemented & green** — 19 step sub-tests across the 6 demo scenarios
> pass on the local model. The demo keeps `ScenarioTests` (standalone, JUnit-free
> contract) and adds `ScenarioSuiteTest` (`@Harness`/`@Scenario`).

## Goals

- `@Harness` on a `@SpringBootTest` class + one `@Scenario` method per scenario →
  every `## Step` is a reported sub-test; first failing step fails, rest skipped.
- The app's HTTP target (random server port) is registered **automatically**.
- The core `inquisitor-harness` stays JUnit-free; all JUnit coupling is in `…-junit`.
- No inheritance: a class annotation + a method annotation, both backed by
  extensions (more idiomatic than a base class, which also burns the single
  inheritance slot).

## Design (as built)

### Annotations (`io.inquisitor.harness.junit`)

```java
@Target(TYPE) @Retention(RUNTIME)
@ExtendWith(HarnessExtension.class)
public @interface Harness {
    String scenarioDir() default "classpath:scenarios/";
}

@Target(METHOD) @Retention(RUNTIME)
@TestTemplate
@ExtendWith(ScenarioTemplateProvider.class)
public @interface Scenario {
    String value() default "";   // explicit .md; else derived from the method name
}
```

- **`@Harness`** marks the class and wires `HarnessExtension`
  (`BeforeEachCallback`): it reads `local.server.port` from the Spring context and
  registers `HarnessDefaults.APPLICATION` → `HttpTarget.of("http://localhost:"+port)`.
  No web server (e.g. a SQL-only suite) → registration skipped.
- **`@Scenario`** is a meta-`@TestTemplate`. The method is an empty `void`
  placeholder; the real work is the provider + a per-step callback.

### `ScenarioTemplateProvider` (`TestTemplateInvocationContextProvider`)

- Resolves the markdown: `@Scenario("…")` if given, else
  `<methodName-kebab>.md` under the class's `@Harness#scenarioDir()`
  (`transferBetweenAccounts()` → `transfer-between-accounts.md`).
- Pulls `ScenarioParser` / `ScenarioExecutor` from the Spring context
  (`SpringExtension.getApplicationContext(...)`), parses the scenario, and starts
  **one** `ScenarioEvaluation` (shared conversation).
- Returns one `TestTemplateInvocationContext` per `## Step` (display name = step
  title). Each carries a `BeforeTestExecutionCallback` that, in encounter order,
  runs `evaluation.next()`: PASS → the empty body runs; FAIL → `AssertionError`;
  any later step after a failure → `TestAbortedException` (reported skipped).
- Because each `@Scenario` method is its own container, the report nests
  scenario → steps without a custom `TestEngine` (which a class-level annotation
  would have required, and which wouldn't compose with `SpringExtension`).

### Module layout

```
inquisitor-harness-junit/io/inquisitor/harness/junit
├── Harness                      (class annotation → HarnessExtension)
├── Scenario                     (method annotation → @TestTemplate + provider)
├── HarnessExtension             (BeforeEachCallback: app HTTP target registration)
├── ScenarioTemplateProvider     (provider + per-step execution/skip callback)
└── package-info.java            (@NullMarked)
```

`…-junit` deps: `api` harness + junit-jupiter-api + spring-test + spring-context;
`implementation` spring-core; lombok compileOnly/annotationProcessor.
`…-junit-starter`: `api` `…-junit` + `…-harness-starter` — a consumer's single
test dependency brings the annotations, extensions, and harness autoconfig.

## Demo verification

- **Kept `ScenarioTests`** (standalone harness contract — compatibility test).
- Added `inquisitor-harness-junit-starter` to the demo test deps (alongside
  `…-harness-starter`; both kept explicit since both paths are under test).
- Added `ScenarioSuiteTest`:

```java
@Harness
@SpringBootTest(webEnvironment = RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INQUISITOR_LLM_IT", matches = "true")
class ScenarioSuiteTest {
    @Scenario void accountNotFound() {}
    @Scenario void openAccountAndDeposit() {}
    @Scenario void transferBetweenAccounts() {}
    @Scenario void overdraftRejected() {}
    @Scenario void transactionHistory() {}
    @Scenario void databaseState() {}
}
```

The gate stays an explicit `@EnabledIfEnvironmentVariable` (gating is a consumer
concern, not the harness's).

## Verification (done)

1. `./gradlew build` green; the gated suites self-skip without a model.
2. `INQUISITOR_LLM_IT=true ./gradlew :inquisitor-demo:test --tests …ScenarioSuiteTest
   --rerun-tasks` with the local model + Podman: **19 step sub-tests, 0 failures,
   0 skipped** — one container per `@Scenario` method, one sub-test per step;
   controller tracing aspect confirms real HTTP traffic.

## Docs updated

- `docs/roadmap.md` (05b done), `docs/decisions.md` (JUnit-layer section),
  `CLAUDE.md` (source-layout note), `tasks/task-03-harness-mvp.md` (open item ticked).
