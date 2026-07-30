# Task 14 — Model benchmark (`bench` Gradle task)

A `bench` task on the `io.inquisitor.harness` plugin that **sweeps several actor
models across the same scenario suite and prints a `llama-bench`-style comparison
table to the console** — so you can pick an actor model on evidence (score +
speed) instead of vibes. It runs *exactly* the `evaluate` measurement path (LLM
gate on, LLM-as-judge on) once per model, with **repeats** for variance, and
aggregates into one row per model.

> Status: **📝 planned.** Design discussed 2026-07-16; not yet built. Sibling of
> the deferred C3 `benchmark.yml` idea (task-08 / task-12), but this is a
> **local, interactive** console benchmark, not a CI matrix.

## Why it's not just `evaluate --report table`

`evaluate` is one `Test`-type task = one JVM = one actor model (from
`INQUISITOR_RUN_MODEL`). A benchmark's whole value is **comparison across
configurations** — one row per model. So `bench`'s defining feature is the
**actor-model sweep with a fixed judge**; a single-model table would be
`evaluate` with a different renderer and isn't the point.

## Shape

`bench` lives in `inquisitor-harness-gradle-plugin` alongside `evaluate`.

- **CLI:** `bench --models a,b,c --repeat N [--warmup W] [--verbose]`.
  - `--models` — comma-separated actor model ids to sweep (required).
  - `--repeat N` — measured passes per model (default e.g. 3).
  - `--warmup W` — unmeasured passes discarded before measuring (default 0); the
    first pass pays context-init + server model-load, so throughput measurement
    will want ≥1.
  - `--verbose` — also print per-repeat rows, not just the aggregate.
- **No new measurement code.** Reuse the `evaluate` engine and the **`json`
  renderer as the interchange format** (it already emits a machine-readable
  per-run summary). `bench` = orchestrator + aggregator + console-table formatter
  over `json` summaries.

## Orchestration (the hard part)

Results must survive **across** JVM runs and be aggregated at the Gradle-daemon
level. For each `(model × repeat)`: run the real evaluate path with that model's
env override (`INQUISITOR_RUN_MODEL`), emit a `json` summary to
`build/bench/<model>/<repeat>.json`, then an aggregation step reads them all back
and prints the table.

**Decision — fork the real test run per config (option A), not an in-JVM model
loop (option B).** You cannot cleanly parameterise one `Test` task N ways under
the configuration cache, so `bench` forks the runs itself (JUnit console launcher
/ Gradle tooling API in a forked JVM). Option B (one bench JVM rebuilding the
actor `ChatClient` per model) is a cleaner loop but re-implements scenario
discovery/execution outside JUnit and **drifts from what `evaluate` actually
runs** — a benchmark that measures a different path than your real suite can't be
acted on. Reuse the true path even though the Gradle orchestration is more
awkward. *(This is the main implementation risk — validate the forking mechanism
early.)*

## Metrics & table

Headline is **groundedness score** (the clean actor-capability axis). Keep the
axes from conflating.

```
| model        | runs | steps | score (μ±σ)  | t/step | total  |
| ------------ | ---- | ----- | ------------ | ------ | ------ |
| gemma-4-31b  |   3  |   48  | 0.94 ± 0.02  |  3.2s  | 2m34s  |
| qwen3-32b    |   3  |   48  | 0.86 ± 0.09  |  2.1s  | 1m41s  |
```

- **score (μ±σ)** — mean judge score across repeats **with standard deviation**.
  σ is first-class: a model swinging 0.94 / 0.71 / 0.88 is worse than a steady
  0.85 even at a lower mean; don't hide instability in the mean.
- **t/step**, **total** — mean per-step and mean total wall-clock across measured
  repeats (the throughput analog to `llama-bench`'s tokens/sec).
- **pass/detection rate** — *optional secondary column, clearly labelled, never
  the ranking key*: it conflates actor skill with app correctness (a fault suite
  wants a *low* pass rate). Include only if it reads clearly.
- One **aggregated row per model**; `--verbose` adds per-repeat rows.

## Invariants & guards

- **Fix the judge across the sweep.** Benchmarking actor models fairly needs one
  constant judge; the ranking is **judge-relative, not absolute** — state this in
  the printed header (judge model + base url, as the report headline already does).
- **Warn (or refuse) when a benched actor equals the judge model** — that pairing
  is self-judge-biased and scores anomalously high.
- Judge temperature already 0 (set in the evaluation autoconfig) — keep it.

## Decisions

- Reuse `json` as the cross-run interchange; no new report format. `bench` prints
  to the console only (no artifact), like `llama-bench`.
- Whole-suite repeats (not per-scenario) — simplest, and captures real end-to-end
  variance including server state.
- Not wired into `check`; explicit opt-in, like `evaluate`.
- Never `UP-TO-DATE`/`FROM-CACHE` (non-deterministic measurement), same as
  `evaluate`.

## Tests

- **Plugin functional test** (TestKit): `bench --models m1,m2 --repeat 2` on a
  generated consumer project with a **stub actor + stub judge** (no real LLM) —
  asserts it forks the right number of runs, aggregates the `json` summaries, and
  prints one row per model with a μ±σ score cell; `--verbose` adds per-repeat
  rows; the actor==judge warning fires; sits in the `harness` group; absent from
  `check`.
- **Aggregator unit test**: given canned per-run `json` summaries, computes the
  right μ/σ/t-step/total and formats the table (fixed-width columns, header with
  judge line). Pin the σ math and the empty/one-repeat edge cases.

## Out of scope

- **tokens/sec** — the truest `llama-bench` analog, but only if the
  OpenAI-compatible endpoint reliably returns usage. Defer until confirmed.
- CI `benchmark.yml` matrix runs and the README verified-models table (still
  task-08 C3 — different surface: artifacts/CI, not an interactive console table).
- Sweeping anything other than the actor model (judge sweep, temperature sweep,
  scenario-subset selection) — possible later, not now.

## Verification

- `./gradlew build` — new functional + aggregator tests green via the root
  `check` hook.
- A real `:inquisitor-demo:bench --models <a>,<b> --repeat 3` against local models
  → a comparison table with per-model μ±σ scores and timings, judge line in the
  header, actor==judge warning when applicable.
