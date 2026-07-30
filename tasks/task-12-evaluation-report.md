# Task 12 — Evaluation report (task-08 Phase C2)

Persist and render what an `evaluate` run learns. Today the judge's per-step scores
and findings live only in the in-memory `StepEvaluationRecorder` and die with the
test JVM — the first real run (2026-07-14, gpt-oss-20b actor / Qwen3.6-35B judge,
93.75% score) produced three CONTRADICTED-on-PASS steps *that nobody can explain*,
because the feedback was never written anywhere. C2 closes that gap: a JSON
artifact of record + a Markdown rendering + a console summary, produced even when
the run fails (a failing run is exactly when you want the report).

> Status: **implemented** on `feature/evaluate-gradle-plugin`.

## The mechanism (decided during the task-11 discussion, validated by the first run)

The `evaluate` task is a Gradle `Test` task: the evaluation data lives in the
**forked test JVM** (recorder beans in Spring test contexts), invisible to Gradle.
There is no channel back to the build but files — the JaCoCo pattern (agent writes
`.exec` in the test JVM; a finalizer task renders it):

1. **Test JVM writes the artifacts.** A JUnit Platform **`LauncherSessionListener`**
   (registered via `ServiceLoader` in `inquisitor-harness-evaluation`) fires exactly
   once after the whole test plan — pass or fail — and writes `evaluation.json` +
   `evaluation.md`. Rendering happens where the data lives (full object graph, no
   parsing). The listener **no-ops unless `inquisitor.report.dir` is set**, so plain
   `test` runs (even with evaluation on, as in the demo) write nothing.
   - Recorders live in (possibly several, cached) Spring contexts, and the listener
     can't see them — so recorder instances self-register in a small static
     **`EvaluationReportSession`** registry the listener drains. The only static
     state in the module; package-private, test-JVM-scoped by nature.
   - Why not `@PreDestroy` per context: Spring's TestContext framework closes cached
     contexts in a JVM shutdown hook — ordering vs. other shutdown work is murky and
     concurrent read-modify-write merges into one file get fiddly. One flush point,
     after the test plan, before shutdown hooks.

2. **Gradle side renders nothing — it surfaces.** *(Amended after review: reporting
   must be implicit in `evaluate`, like any `Test` task's HTML report — no separate
   task.)* The plugin registers a `TestListener` on `evaluate` whose root-suite
   `afterSuite` callback reads the headline section of `evaluation.md` and prints it
   with the artifact path. `afterSuite` fires after all tests but *before* the task
   fails on failing tests, so the echo survives a red run without a finalizer.
   Silent when no report exists. No JSON parsing (and no Jackson) in the plugin.
   (The original design's separate `evaluateReport` finalizer task was implemented,
   then removed as redundant surface.)

## Changes

### 1. `inquisitor-harness-evaluation` — richer records

`StepEvaluationRecord` grows to carry what the report needs (all of it is already
in `EvaluationStepRunner`'s hands):

- `scenarioSource` — `Scenario.source()`; **required disambiguator**. The suite
  class name (`ExplicitScenarioSuiteTest` vs `CucumberScenarioSuiteTest`, …) never
  reaches the evaluation layer: the recorder is fed via `StepRequest(conversationId,
  userMessage, Scenario, Step)` and the dependency arrow is junit → harness →
  evaluation seam, never backwards (the standalone executor path has no suite class
  at all). The buckets differ only by `@Harness(scenarioDir)`, and that directory is
  exactly what `source` encodes (`scenarios/explicit/….md` vs
  `scenarios/cucumber/….md`) — same `name`, different file, different markdown. It
  is also the better report key: it points at the artifact that actually ran.
- actor verdict: `outcome`, `reasoning`, `evidence` (the judge's sharpest check is
  evidence-vs-trace; the report should show both sides).
- `toolCalls` — the rendered trace lines (`ToolCallRecord.describe()`), so a
  finding can be checked against the evidence without rerunning.
- `elapsed` — `StepRun.elapsed()` (per-step actor timing).
- existing: `scenario`, `stepIndex`, `stepTitle`, `score`, `category`, `feedback`.

`StepEvaluationRecorder.record(...)` signature widens accordingly (it already
receives the `StepRun` context via `EvaluationStepRunner`).

### 2. Synthetic verdicts are not actor claims

The first run scored a harness-synthesized FAIL ("model returned an empty or
unparseable response") as CONTRADICTED — technically right, semantically noise:
there is no actor claim to audit. Change:

- `LlmStepRunner.verdictOrFail(...)` marks the fabricated verdict — additive
  `synthetic` component on `StepVerdict` (`false` for real verdicts; compact
  canonical constructor keeps all call sites source-compatible… **verify**: record
  components are positional, so this is a **new component** — check call sites and
  keep a convenience constructor with `synthetic = false`).
- `EvaluationStepRunner` skips the judge for synthetic verdicts and records
  category **`NOT_EVALUATED`** (score excluded from the mean; counted in its own
  report column). The judge audits claims; a parse failure is a *run-health* fact,
  not a groundedness fact.

### 3. Expectation-aware gates (decided in task-11)

The report's deterministic gate is **"outcome matches expectation"**, not "all
PASS" — fault suites (`@Scenario(expect = FAIL)`) are part of `evaluate` runs and
a detected fault is a *good* outcome (the first run: all 3 bugs caught, and those
grounded FAILs must not read as failures).

Plumbing: the recorder can't see the JUnit annotation, so the **core `Scenario`
model gains `expectedOutcome`** (`Outcome`, default `PASS`):

- parser always produces `PASS` (markdown has no expectation syntax);
- `inquisitor-harness-junit`'s `ScenarioTemplateProvider` copies the parsed
  scenario with `FAIL` when `@Scenario(expect = FAIL)` — a `withExpectedOutcome`
  wither on the record;
- the standalone executor path is untouched (its callers assert expectations
  themselves).

Per-scenario gate in the report: last recorded step outcome vs. `expectedOutcome`
(a FAIL-expected scenario that never fails is a **missed detection**). This is
also the groundwork for task-07's deferred detection-%.

### 4. Report writing (`inquisitor-harness-evaluation`)

- `EvaluationReport` / `EvaluationReportWriter`: aggregates all registered
  recorders' records into one document:
  - **run info**: timestamp, wall duration (session start→close), header label
    (`inquisitor.report.header`), actor model + base-url
    (`spring.ai.openai.chat.model` / `.base-url`), judge model + base-url
    (`inquisitor.harness.evaluation.*`) — captured from the `Environment` by the
    starter at recorder-creation time (the listener has no context).
  - **aggregate**: steps evaluated, mean score, category histogram,
    expectation-gate result (scenarios matched / total, missed detections),
    `NOT_EVALUATED` count.
  - **grouped by bucket** — the parent directory of `source` (e.g. `explicit`,
    `cucumber`, `intent`; scenarios without a source fall into a `(no source)`
    bucket). Each bucket gets its own aggregate (score, gate) so a
    style-bucket comparison reads directly off the report — the baseline run's
    key insight (cucumber weakest) surfaced this way.
  - **per scenario within a bucket** (keyed name + source): expectation, per-step
    rows (index, title, outcome, category, score, elapsed) and, for every
    non-`GROUNDED` step, the judge's findings + the actor's reasoning/evidence +
    the tool trace.
- `evaluation.json` via Jackson 3 (`tools.jackson`; explicit dependency — today it
  arrives only transitively). `evaluation.md` rendered from the same model: a
  headline block (the future verified-models row: header, actor, judge, passed-%
  gate, evaluation score, duration) then per-scenario sections.
- **Decided:** both renderings live here in the test JVM, next to the data — the
  Gradle side never renders. Possible later refactor (not now): extract the
  renderers into a dedicated render module if they grow (multi-config aggregation
  in C3 may force that anyway).
- `EvaluationReportSessionListener` (`LauncherSessionListener`, `ServiceLoader`
  registration): on `launcherSessionClosed`, if `inquisitor.report.dir` is set and
  any records exist → write both files; always clear the registry.

### 5. Gradle plugin

- `evaluate` sets `inquisitor.report.dir` → `layout.buildDirectory.dir("reports/inquisitor")`
  (execution-time provider, configuration-cache-safe, same pattern as the header).
- New `evaluateReport` task (`harness` group), `evaluate.finalizedBy(evaluateReport)`;
  prints the headline block of `evaluation.md` + both artifact paths; `onlyIf` the
  report exists (so consumers without the evaluation modules see nothing).

### 6. Tests

- **Evaluation module**: recorder enrichment; writer golden tests (JSON shape, MD
  headline) over a hand-built record set including a `NOT_EVALUATED` and a
  missed-detection case; listener no-op without the sysprop / writes with it.
- **Harness core**: `StepVerdict.synthetic` + `Scenario.expectedOutcome` additions.
- **JUnit module**: provider maps `expect = FAIL` → `expectedOutcome`.
- **Plugin functional test**: the generated consumer project's tagged test writes a
  canned `evaluation.md` into the report dir (simulating the listener; the real
  modules need a judge model — out of TestKit scope) and *fails*; assert the build
  fails **but** `evaluateReport` still runs and prints the headline. Plus: probe
  test asserts the `inquisitor.report.dir` sysprop points into `build/reports/inquisitor`.

### 7. Docs

- `tasks/task-08-evaluation.md`: §5/§6 status — C2 landed, C3 (README verified-models
  table + publishing + `benchmark.yml`) remains.
- `docs/roadmap.md` row; `docs/decisions.md`: the file-handoff/JaCoCo pattern, the
  session-listener + static-registry choice, synthetic-verdict exclusion,
  expectation-aware gates.
- `CLAUDE.md`: module map line for the evaluation module (report writer), plugin row
  (`evaluateReport`).

## Out of scope (C3 / later)

- README verified-models table generation from `evaluation.json`; `benchmark.yml`
  multi-config runs; publishing the plugin.
- Actor system-prompt calibration ("compare numbers by value, not formatting" —
  cost two cucumber scenarios in the first run); separate small change, alters run
  behaviour, keep out of the reporting diff.
- Evaluation-as-gate (`fail-under`, `@Grounded`) — roadmap "later idea" unchanged.

## Verification

1. `./gradlew build` — green, no behaviour change (listener no-ops without the
   sysprop; synthetic/expectation fields additive).
2. `./gradlew :inquisitor-demo:evaluate` against the local models → build outcome
   as before **plus** `build/reports/inquisitor/evaluation.{json,md}`; console
   headline after the (failing or passing) test task; the three
   CONTRADICTED-on-PASS steps now come with the judge's findings attached.
3. Plugin functional tests green (report finalizer runs on failure).
