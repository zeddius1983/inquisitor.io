# Roadmap

Volatile status — what's done and what's next. For stable repo context see
[../CLAUDE.md](../CLAUDE.md); for the rationale behind choices see
[decisions.md](decisions.md).

The authoritative status is git history + this table.

## Status

| # | Task | Status |
|---|------|--------|
| 01 | Gradle 9 multi-module build scaffold | ✅ done |
| 02 | `inquisitor-demo` banking REST service | ✅ done |
| —  | `inquisitor-demo-db-starter` (zero-config local Postgres) | ✅ done |
| 03 | `inquisitor-harness` — core scenario execution (parser, tools, executor) | ✅ done |
| 04 | `inquisitor-harness-starter` — autoconfiguration | ✅ done |
| 05 | Standalone harness wired into `inquisitor-demo` (`@Test`-per-scenario), green on the local model | ✅ done |
| 05b | `inquisitor-harness-junit` + `-junit-starter` (`@Harness`/`@Scenario` ergonomic layer) | ✅ done |
| —  | Scenario style buckets (`explicit`/`cucumber`/`intent`); flattened (no `positive`/`negative` split) | ✅ done |
| 09 | OpenAPI/Swagger discovery — optional `inquisitor-harness-openapi` plugin (`OpenApiAdvisor`) | ✅ done |
| 07 | Fault detection (oracle calibration) — runtime fault router + standalone & `@Harness` suites | ✅ Phase 1 & 2 done (`tasks/task-07`) |
| 08 | Step evaluation (`harness:evaluate`) — trustworthy-green + model report | 🚧 in progress — core done (`StepRunner` seam, tool-call ledger, evaluator + autoconfig); report pending (`tasks/task-08`) |
| 11 | `io.inquisitor.harness` Gradle plugin — `evaluate` task (task-08 Phase C1) | ✅ done — plugin + TestKit test (`tasks/task-11`); demo `includeBuild` wiring + report (C2) pending |
| 10 | OpenAPI context-size optimisation — deterministic digest + partial retrieval (size-gated) | 📝 planned (`tasks/task-10`) |
| 06 | `inquisitor-mock` + `inquisitor-mock-starter` | ⏳ reserved, not started |

## Now

- Phase 05b is green: a `@Harness` `@SpringBootTest` class with one `@Scenario`
  method per scenario reports **one sub-test per step** (19 step-tests across the
  6 demo scenarios, all PASS on the local model). Each `@Scenario` method resolves
  its markdown by name and the app's HTTP target is auto-registered from the
  random port — no per-scenario boilerplate. The standalone `ScenarioTests` is
  kept as the JUnit-free compatibility test.

## Next

- **OpenAPI discovery is in** (task-09): the optional `inquisitor-harness-openapi`
  module injects the app's spec into the system prompt via an `OpenApiAdvisor`, so
  scenarios can be written as natural-language **intent** with no endpoints (see the
  `scenarios/intent` bucket + `IntentScenarioSuiteTest`). The demo serves a
  static `openapi.yaml` at `/v3/api-docs.yaml`; real consumers can use springdoc.
- **Fault detection is in** (task-07): oracle calibration via mutation testing — correct
  scenarios run against a deliberately buggy build (a `@Primary` `AccountServiceRouter`
  switching in `BuggyAccountServiceImpl` per enabled `Bug`), with the model expected to
  report the failure. Phase 1 is the standalone `FaultDetectionTests`; Phase 2 brings it
  to the ergonomic `@Harness` layer via `@Scenario(expect = FAIL)` (core) + `@EnableBug`
  (demo), exercised by `FaultDetectionSuiteTest`.
- **Step evaluation** (task-08) is in progress: a `harness:evaluate` mode that scores
  how *earned* each green verdict is — a second, independent LLM judges the actor's claim
  against the real tool-call trace (captured via `ToolContext`), producing a per-suite
  **evaluation score** (`100% passed / 85% score`). **Core has landed**: the `StepRunner`
  seam (`LlmStepRunner` + `EvaluationStepRunner`), the `ToolCallRecord` ledger,
  the `StepEvaluator`/`StepEvaluationRecorder`, all now extracted into optional
  `inquisitor-harness-evaluation` / `-starter` modules (like OpenAPI) and gated on
  `inquisitor.harness.evaluation.enabled` (off by default). The core keeps only the
  trace seam (`TraceKeys`/`ToolCallRecord`/`StepRun`); the starter decorates the
  harness's `ToolCallback` beans via a `BeanPostProcessor`. The `io.inquisitor.harness`
  Gradle plugin with the `evaluate` task has landed too (task-11, Phase C1).
  **Pending**: the demo `includeBuild` wiring and the JSON + Markdown report (C2). See
  `tasks/task-08-evaluation.md`. **Later idea — evaluation as an opt-in gate**:
  today the score is calibration-only (an actor-PASS judged `CONTRADICTED` still
  reports green in JUnit); two complementary opt-in gates are on the table.
  (a) A coarse `inquisitor.harness.evaluation.fail-under=1.0` threshold (for the
  standalone suites / the future `evaluate` Gradle task) that turns an ungrounded
  PASS into a real failure. (b) A per-scenario **`@Grounded`** annotation for the
  `@Harness` layer: shipped *by the evaluation module* with its own JUnit
  extension reading the `StepEvaluationRecorder`, so `inquisitor-harness-junit`
  stays ignorant of evaluation (no new core seam, dependency arrow stays
  evaluation → junit); the step whose PASS the judge rejects fails with the
  judge's findings as the message. Must fail loudly if evaluation is off. Both
  deliberately deferred until after the C2 report — surface the score first,
  gate on it second, since it makes a non-deterministic judge a hard gate — and
  **need a fresh design review when picked up** (open: `@Grounded` on class vs
  method, interplay with `expect = FAIL`, how the extension maps recorder
  entries back to step sub-tests).
- **OpenAPI context-size optimisation** (task-10) is planned: size-gated modes in the
  openapi plugin instead of injecting the whole raw spec every round-trip — a
  **deterministic digest** (compact operation signatures + a type dictionary, rendered
  faithfully, *not* via an LLM) for medium specs, and **partial retrieval** (a
  table-of-contents + `getOperationSpec` lookup tool, with vector RAG as the escalation)
  for very large ones. No core change.
- Decide on the mock-server design before implementing the `*-mock` modules.

> Keep this file current as tasks complete; move durable rationale into
> [decisions.md](decisions.md), not here.
