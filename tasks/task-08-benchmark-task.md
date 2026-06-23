# Task 08 — `benchmark` Gradle task (trustworthy-green + model report)

A Gradle task that runs the demo scenarios against a specified/configured model
and produces a **report** (pass %, detection %, elapsed time, per-scenario
detail) — but, crucially, it does **not** trust the LLM's own verdict. It
independently verifies, from the actual execution trace, that the agent really did
the work: the required tools were called with the right arguments, the responses
the model claimed match what the tools actually returned, and no steps were
skipped or fabricated.

> Status: **planned — awaiting review.** No code yet. Open design decisions are
> marked **[DECIDE]**.

## Why

The LLM is both the **actor** (drives the app under test) and the **judge** (emits
the verdict). A green run today means trusting the judge — and the judge has been
caught lying: `gemma-4-12B` (reasoning off) skips an import call and rubber-stamps
the step; `GLM-4.7-Flash` (reasoning off) fabricates whole responses. The only way
to trust green is to verify against a source that **isn't the LLM**: the actual
tool-call trace.

This is complementary to [task-07](task-07-negative-scenarios.md):

- **task-07** — does the *judge* catch a deliberately wrong assertion? (specificity)
- **task-08** — did the *agent* actually execute, and is its green verdict
  *earned*? (anti-hallucination / ground truth)

They share one piece of infrastructure — a structured record of every tool call —
which should be **built once and reused by both**.

Bonus payoff: this **auto-generates the README "Verified models" table** (model ×
quantization × reasoning → passed %, detection %, duration) that is currently
hand-maintained, and is a real consumer feature: benchmark *your* model choice
against *your* scenarios before trusting it.

## Goals

- A `benchmark` Gradle task that runs the demo scenario suite against a given model
  and emits a structured report.
- Verify each scenario independently of the LLM verdict, using the recorded tool
  trace: required calls happened (with key args), claimed responses match actual
  results, all steps ran (none skipped), all verdicts PASS.
- Measure per-scenario and total elapsed time.
- Support a single run and a **matrix** (models × reasoning on/off) that produces
  the verified-models table.
- No change to the harness's public behaviour; tool-call capture is additive and
  test/benchmark-scoped.

## Design

### 1. Structured tool-call capture (shared with task-07)

Record every tool invocation as typed data, **not** by parsing log lines (the
DEBUG logs in `HttpRequestTool`/`SqlTool` stay for humans; the benchmark needs a
stable structure). A `ToolCallRecord` ledger collected per scenario run:

```
ToolCallRecord(tool, arguments, result, order, elapsed)
```

**[DECIDE]** capture mechanism — two viable options:

- **(a) Recording decorator** around the registered tools (a `ToolCallback`
  wrapper, or decorate `HttpRequestTool`/`SqlTool` in a benchmark Spring profile)
  that appends to a request-scoped/run-scoped ledger. Simple, fully under our
  control.
- **(b) Micrometer `ObservationHandler`** on Spring AI's tool-call observations,
  registered only in the benchmark context. No tool wrapping; leans on framework
  instrumentation. Verify the observation actually carries args + result with
  enough fidelity before committing.

Recommendation: start with (a) — least magic, guaranteed fidelity — and revisit
(b) if we want zero wrapping.

### 2. Expected execution profile (ground truth, never sent to the LLM)

Each scenario gets a sidecar describing what a correct run must do — e.g.
`import-accounts.expected.yml` next to (or mirroring) the scenario. **It must never
be included in the prompt**; it's harness-only ground truth.

Key principle: assert **required** calls, not exact totals (a good model may add an
extra exploratory GET; demanding "exactly N" would false-alarm). Match on the facts
that matter, not whole payloads (brittle).

Sketch:

```yaml
# import-accounts.expected.yml — NOT sent to the model
requiredCalls:
  - tool: sqlQuery
    matches: { sqlContains: "TRUNCATE" }
  - tool: httpRequest
    matches: { method: POST, pathContains: "/accounts/import", headersContain: "text/csv" }
  - tool: httpRequest
    matches: { method: POST, pathContains: "/accounts/import", headersContain: "text/plain" }
  - tool: sqlQuery
    matches: { sqlContains: "SELECT", sqlContains2: "account" }
expectAllStepsRan: true        # results.size() == scenario.steps().size()
expectAllVerdicts: PASS
```

**[DECIDE]** sidecar format/location (`*.expected.yml` beside scenario? a single
`benchmark/` manifest? embedded fenced block the parser strips before prompting?).

### 3. The checks (independent of the LLM verdict)

Per scenario, after running it through the existing `ScenarioExecutor` (which yields
`ScenarioResult` with per-step verdicts), cross-check against the ledger:

1. **No skipped steps** — `result.results().size() == scenario.steps().size()`.
2. **All verdicts PASS** — every `StepVerdict.outcome() == PASS` (positive suite).
3. **Required calls present** — each `requiredCalls` entry matches at least one
   `ToolCallRecord` (this catches the 12B "skipped the import call" failure: the
   `text/plain` POST is required and absent).
4. **No fabrication** — cross-reference the model's verdict *evidence* against
   actual tool results: a status code / id / count the model claims must appear in a
   real `ToolCallRecord.result`. **Best-effort / fuzzy**: evidence is paraphrased
   free text, so match on key facts (status, ids, counts), not string equality.
   This is the check that catches GLM-off inventing responses.
5. **Final-state spot check [DECIDE / optional]** — independently re-query the DB
   or API after the run to confirm persisted state, rather than relying on the
   scenario's own verify step.

A scenario is "**truly passed**" only if the LLM said PASS *and* checks 1–4 hold.
The gap between "LLM says pass" and "truly passed" is the false-positive rate — the
headline number.

### 4. Gradle surface

A custom task (**[DECIDE]** buildSrc task type vs. task in `:inquisitor-demo` build
vs. a new `:inquisitor-benchmark` module). First cut: a task in the demo build.

```bash
./gradlew :inquisitor-demo:benchmark -Pmodel=gemma-4-31B-it-QAT-Q4_0 -Preasoning=off
# matrix:
./gradlew :inquisitor-demo:benchmark -Pmatrix
```

- Reads `-Pmodel` / `-PbaseUrl` / `-Preasoning` (or a `benchmark { … }` extension /
  config file); sets the `OPENAI_*` env + `INQUISITOR_LLM_IT=true`.
- Drives a dedicated **benchmark runner** (a JUnit `Launcher` programmatic run, or a
  small `main`) — separate from the normal `test` task so a plain build is unaffected
  and the benchmark can run a matrix.
- **[DECIDE]** matrix source: hardcoded list, a `benchmark.yml`, or task params.

### 5. Report

- **Console summary** + a written artifact (**[DECIDE]** Markdown / JSON / both;
  Markdown lets it drop straight into the README table).
- Columns mirror the README "Verified models" table: model, quantization, reasoning,
  **passed %** (truly-passed, not LLM-claimed), **detection %** (from task-07 if
  present), **duration**, plus per-scenario detail (required-call hits/misses,
  fabrication flags, per-step verdicts and timings).
- The matrix mode emits the whole table — replacing the manual paste.

## Hard parts / risks (call out honestly)

- **Non-determinism** — call counts and timings vary run to run; expectations must be
  "required subset", not exact equality, and the report should be read as a benchmark,
  not a hard gate. Consider N repeats for a stability number. (Cost: runs are 3–14 min
  each; a full matrix is long — make single-model the default, matrix opt-in.)
- **Fabrication check is fuzzy** — paraphrased evidence means key-fact matching, with
  inevitable false neg/pos. Document it as best-effort; the required-call check
  (deterministic) is the stronger signal.
- **Expected-profile maintenance** — every scenario needs a hand-written profile;
  keep it minimal (required calls + step/verdict counts) to avoid brittleness.
- **Profile leakage** — guarantee the expected profile is never fed to the LLM.

## Out of scope (possible follow-ups)

- Extracting a **reusable Gradle plugin** so consumers benchmark their own suites
  (the natural v2 once the demo task proves the shape).
- Semantic (LLM-assisted) fabrication detection instead of key-fact matching.
- CI integration / trend tracking across commits.

## Relationship to task-07

Build the **tool-call ledger** (§1) first; both tasks consume it. task-07 asserts a
FAIL is correctly produced; task-08 asserts a PASS is *earned*. Sequencing
**[DECIDE]**: ledger → task-08 → fold task-07's detection % into the report.

## Verification (when implemented)

1. `./gradlew build` unaffected (benchmark is a separate task; gated runner self-skips
   without a model).
2. `:inquisitor-demo:benchmark -Pmodel=<31B> -Preasoning=off` → all scenarios
   truly-passed; report shows full required-call coverage, 0 fabrication flags.
3. Re-run against `gemma-4-12B -Preasoning=off` → benchmark **independently flags**
   the import scenario (missing `text/plain` required call) even though the LLM
   verdict claimed PASS — i.e. it catches the false positive we already know exists.
4. Matrix mode reproduces the README "Verified models" table from measured data.

## Docs to update

- `docs/roadmap.md` (new task row), `docs/decisions.md` (why independent trace
  verification + the capture-mechanism choice), `README.md` (auto-generated verified-
  models table; `benchmark` task usage), `CLAUDE.md` (build commands), this file
  (status → implemented).
