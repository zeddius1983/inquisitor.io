# Task 11 — `io.inquisitor.harness` Gradle plugin (`evaluate` task) — task-08 Phase C1

Stand up the consumer-facing entry point for step evaluation
([task-08](task-08-evaluation.md)): a real, publishable Gradle plugin whose
**`evaluate`** task runs the tagged scenario tests with the LLM gate and
LLM-as-judge evaluation switched on. This is **plugin-first** — the plugin lands
before any reporting, deliberately inverting task-08 §6's "demo-task-first"
sequencing. Reporting (C2), the README verified-models table + docs (C3), and
`benchmark.yml`/publishing come later.

C1's job is exactly: **the plugin exists and its `evaluate` task runs the tagged
scenario tests with the gate + evaluation enabled.** No report is produced yet.

> Status: **implemented** on `feature/evaluate-gradle-plugin`.

## Decisions (locked during the task-08 Phase C discussion)

- **New module**, not `buildSrc` — this is a published *product* plugin a consumer
  applies, not an in-repo convention plugin.
- The task is a Gradle **`Test`-type** task (reuses Gradle's JUnit Platform
  integration and its results/reporting pipeline), not a hand-rolled JUnit
  `Launcher` main.
- **Models come from `application.yml`** (actor: `spring.ai.openai.chat.model`;
  judge: `inquisitor.harness.evaluation.model`). The only task input is a literal
  `-Pheader="…"` label destined for the (future C2) report — no
  `-Pmodel/-Pjudge/-Pquantization/-Preasoning` option sprawl. Override keys and
  `benchmark.yml` are a later stage.
- **Consumption: TestKit first, `includeBuild` later.** C1 validates the plugin
  with a Gradle TestKit functional test only. A follow-up step (before C2) adds
  `includeBuild("inquisitor-harness-gradle-plugin")` so `inquisitor-demo` can apply
  `id("io.inquisitor.harness")` and `:inquisitor-demo:evaluate` runs against real
  local models — which is what lets us iterate on C2 reporting. Publishing to the
  plugin portal / Maven Central is separate, and later still.

## Changes

### 1. New module `inquisitor-harness-gradle-plugin`

- `settings.gradle.kts`: add `"inquisitor-harness-gradle-plugin"` to the
  `include(...)` list.
- `inquisitor-harness-gradle-plugin/build.gradle.kts`:

  ```kotlin
  plugins {
      id("inquisitor.java-conventions")   // toolchain 26 / release 21 / useJUnitPlatform / junit-bom
      `java-gradle-plugin`
  }

  gradlePlugin {
      plugins {
          create("inquisitorHarness") {
              id = "io.inquisitor.harness"
              implementationClass = "io.inquisitor.harness.gradle.InquisitorHarnessPlugin"
              displayName = "Inquisitor harness"
              description = "Runs Inquisitor scenario tests with LLM-as-judge evaluation."
          }
      }
  }

  dependencies {
      testImplementation(gradleTestKit())   // junit-jupiter comes from java-conventions
  }
  ```

- The functional test uses plain JUnit Jupiter assertions (no AssertJ — it isn't
  in the version catalog and this module has no `spring-boot-starter-test` to
  bring it in).
- `release = 21` (inherited from `java-conventions`) keeps the plugin loadable by
  Gradle running on Java 21+.
- Publishing (`publish-conventions` / plugin-portal) is **deferred** — not applied
  in C1. The plugin is **not** added to the `inquisitor-bom` constraints.

### 2. Plugin implementation (plain Java, package `io.inquisitor.harness.gradle`)

- `package-info.java` — `@NullMarked` (repo convention).
- `InquisitorHarnessExtension` — a minimal DSL: `SetProperty<String> tags`
  defaulting to `["inquisitor"]` (which JUnit tag(s) the `evaluate` task selects).
- `InquisitorHarnessPlugin implements Plugin<Project>`:
  - Registers work under `project.getPlugins().withType(JavaPlugin.class, …)` so
    it's robust to apply order and only wires when there's a test source set.
  - Creates the `harness` extension, then registers **`evaluate`** as a `Test`
    task:
    - `group = "harness"`, description as above.
    - `testClassesDirs` / `classpath` from the `test` `SourceSet` (gives the
      compiled test classes + their runtime deps).
    - `useJUnitPlatform(o -> o.includeTags(extension.tags…))` — selects the
      scenario tests by tag.
    - `environment("INQUISITOR_LLM_IT", "true")` — the `@RequiresLlm` gate
      (`RequiresLlmCondition.ENABLED_ENV`).
    - `environment("INQUISITOR_EVAL", "true")` — flips
      `inquisitor.harness.evaluation.enabled` via the existing config plumbing.
    - Header passthrough: `providers.gradleProperty("header")` → system property
      `inquisitor.report.header` (configuration-cache-safe via a provider;
      **unused until C2**, wired now so the contract is stable).
    - **Not** made a dependency of `check` — an explicit opt-in task.

### 3. TestKit functional test

`src/test/java/io/inquisitor/harness/gradle/InquisitorHarnessPluginFunctionalTest`,
using `GradleRunner` + `withPluginClasspath()` against a generated temp project:

- Emits a build script applying `id("io.inquisitor.harness")` + JUnit 5, with two
  tests: one `@Tag("inquisitor")` test asserting `System.getenv("INQUISITOR_EVAL")`
  and `INQUISITOR_LLM_IT` are `"true"` (proves the env wiring end-to-end), and one
  **untagged** test that must be skipped.
- Runs `gradle evaluate`; asserts `BUILD SUCCESSFUL`, the tagged test executed,
  the untagged one did not, and `evaluate` sits in the `harness` group.
- A second run of `gradle check` asserts the tagged test does **not** run there
  (`evaluate` is not wired into `check`).

### 4. Light docs

- `CLAUDE.md` — add the module-map row for `inquisitor-harness-gradle-plugin`.
- `tasks/task-08-evaluation.md` — flip §6's status note: the plugin + `evaluate`
  task have landed (C1); reporting is the remaining work. (Full README usage +
  `docs/decisions.md` entries stay in C3.)

## Follow-up step (before C2, not part of C1): `includeBuild` into the demo — **done**

`includeBuild("inquisitor-harness-gradle-plugin")` (root `settings.gradle.kts`) lets
`inquisitor-demo` apply `id("io.inquisitor.harness")` and makes
`:inquisitor-demo:evaluate` work in-repo. What it took:

- **The module converted from subproject to standalone included build** — a directory
  can't be both, and plugin-id resolution requires an included build. Consequences:
  its own `settings.gradle.kts` (sharing the root version catalog via
  `from(files("../gradle/libs.versions.toml"))`), its own `gradle.properties`
  (group/version — the root's isn't inherited), and the `inquisitor.java-conventions`
  overlap inlined into its `build.gradle.kts` (`buildSrc` isn't visible to included
  builds). Its `check` is hooked into the root `check` so `./gradlew build` / CI keep
  running its functional tests (included builds are skipped by default).
- **No consumer-visible `@Tag`**: `@Scenario` itself is meta-annotated
  `@Tag("inquisitor")` (JUnit supports tags on composed annotations), so *every*
  `@Scenario` method is selectable by the `evaluate` task with zero annotation burden —
  a test containing `@Scenario` is evaluation-ready by construction. Pinned by
  `ScenarioTagDiscoveryTest` in `inquisitor-harness-junit` (launcher discovery with
  `includeTags("inquisitor")` selects `@Scenario` methods — including
  `expect = FAIL` — and not plain `@Test` methods). Consequences:
  - the standalone `ScenarioTests` / `FaultDetectionTests` (plain `@Test` driving the
    executor) are *not* part of `evaluate` — for the demo that also dedupes the
    explicit bucket, which `ExplicitScenarioSuiteTest` already covers there;
  - `FaultDetectionSuiteTest` (`@Scenario(expect = FAIL)`) **is** selected. Its JUnit
    outcome is green either way (a failing step is the success condition), and the
    judge scores verdict *groundedness*, not passed-ness — a correctly-detected fault
    is a `GROUNDED` FAIL. The C2 report must therefore be **expectation-aware**: the
    deterministic gate is "outcome matches `expect`" (not "all verdicts PASS"), which
    also lays the ground for task-07's deferred detection-% metric.
- The clobber worry resolved in our favour: the conventions'
  `withType<Test> { useJUnitPlatform() }` action was registered *before* the plugin's
  task, so it runs first and the plugin's `useJUnitPlatform(includeTags)` lands after
  it on the same framework instance. Verified deterministically:
  `:inquisitor-demo:evaluate --tests <untagged class>` fails with "no tests found" —
  the tag filter is intersecting.

## Notes

- `-Pheader` and `INQUISITOR_EVAL` are wired but do nothing visible until C2 adds
  the recorder → report path.

## Verification

- `./gradlew :inquisitor-harness-gradle-plugin:test` — the TestKit functional test
  proves the `evaluate` task is registered, selects the tagged tests, wires the
  gate + eval env, and is excluded from `check`.
- `./gradlew build` — the new module compiles and the whole build stays green;
  nothing else changes behaviour (no demo wiring, evaluation still off by default).
