# Task 07 — Negative scenarios (oracle calibration)

A set of **intentionally failing** scenarios that verify the model correctly
reports `FAIL` when a step's expected outcome contradicts reality. Where the
positive suite measures *sensitivity* (a good run isn't wrongly failed), these
measure *specificity* — that the oracle catches a wrong answer instead of
rubber-stamping it.

This is the harness's most important safety property: an LLM judge's scariest
failure mode is the **false positive** (a green verdict on a step it never truly
verified). We've already observed it — `gemma-4-12B` (reasoning off) skips an
import call and rubber-stamps the step; `GLM-4.7-Flash` (reasoning off) fabricates
responses for whole scenarios. This task turns "the model might rubber-stamp" from
a caveat in the README into a measured number.

> Status: **planned — awaiting review.** No code yet.

## Goals

- A small suite of negative scenarios where a `FAIL` verdict at a known step is
  the **success condition**.
- Cover three shapes: fully-wrong, partially-wrong, and plausible-but-wrong.
- Keep them out of the positive suites (`ScenarioTests`, `ScenarioSuiteTest`) so
  they don't turn the build red for the wrong reason.
- **No core/framework change.** Wire them through the existing standalone contract
  (the harness already returns `ScenarioResult` with per-step verdicts), so this
  is purely a demo-side addition.
- Same gating as the positive suite (`INQUISITOR_LLM_IT=true`); treat results as a
  calibration benchmark, not a hard CI gate (see Non-determinism below).

## Design

### Where the scenarios live

New directory `inquisitor-demo/src/test/resources/scenarios-negative/`, **separate
from** `scenarios/`. The `@Harness` ergonomic suite (`ScenarioSuiteTest`) defaults
`scenarioDir` to `classpath:scenarios/`, so a separate dir guarantees the positive
suites never pick these up.

### Test class

New `NegativeScenarioTests` in the demo, mirroring `ScenarioTests` (standalone
harness: `@Autowired ScenarioExecutor`/`ScenarioParser`/`HttpTargetRegistry`,
`@LocalServerPort`, app target registered in `@BeforeEach`), gated with
`@EnabledIfEnvironmentVariable(INQUISITOR_LLM_IT=true)`.

It inverts the assertion. Instead of `assertThat(result.passed()).isTrue()`, it
asserts the scenario failed **at the expected step**, using the existing model API:

```java
private void runExpectingFailureAt(String classpathLocation, String expectedStepTitleFragment) {
    val resource = new ClassPathResource(classpathLocation);
    val scenario = parser.parse(read(resource), resource.getFilename());
    val result = executor.evaluate(scenario);

    // The model must NOT have passed everything — that would be a false positive.
    val failure = result.firstFailure();
    assertThat(failure)
            .withFailMessage(() -> "Expected the model to catch the false assertion at a step "
                    + "containing \"" + expectedStepTitleFragment + "\", but the scenario passed:\n"
                    + describe(result))
            .isPresent();

    // …and it must fail at the step we deliberately broke, not somewhere upstream.
    assertThat(failure.get().step().title())
            .withFailMessage(() -> "Scenario failed at the wrong step:\n" + describe(result))
            .containsIgnoringCase(expectedStepTitleFragment);
}
```

`describe(...)` is the same verdict-dump helper already in `ScenarioTests` (lift it
to a shared test util, or duplicate — it's small). Reusing it means a failure
message shows the model's reasoning/evidence, which is exactly what you want when
diagnosing a false positive.

Note on `firstFailure()`: with fail-fast, `results` holds every step up to and
including the first failure, so for a partially-wrong scenario the steps before the
broken one are confirmed to have genuinely passed (the assertion that the break is
at step N implicitly checks steps 1..N-1 passed).

### The three scenarios

All reuse the demo's banking domain so no app changes are needed.

1. **`fully-wrong.md`** — every step's expectation contradicts reality. E.g. create
   an account (real `201`) but the **Expected response** asserts `500` and a
   non-existent error body. Expect `FAIL` at step 1. Catches gross rubber-stamping
   (a model that fails here is unusable as an oracle).

2. **`partially-wrong.md`** — steps 1–2 correct, step 3 false. E.g. create account
   → deposit `100` (both truthfully asserted), then **Step 3** asserts the balance
   is `999.00`. Expect `PASS, PASS, FAIL`. This is the key **discrimination** test:
   it checks the model fails *only* the broken step, not the whole scenario, and
   doesn't lazily pass step 3 from the momentum of two prior passes.

3. **`plausible-but-wrong.md`** — a subtle, reasonable-looking false assert that
   only a careful read catches. E.g. open an account in `USD`, then assert the
   response currency is `EUR`; or import N accounts and assert a count of `N+1`.
   Expect `FAIL` at that step. Catches a model that skims rather than verifies.

Each `.md` carries a top note stating it is an intentional negative fixture and
which step is expected to fail, so a human reader isn't confused.

## Optional: detection-rate metric for the model table

This gives the README "Verified models" table a second axis beyond runtime and
positive pass-rate: **detection rate** = % of negative scenarios whose injected
failure the model correctly caught. Likely outcome based on what we've seen:
`*-off` configs that fabricate/skip will score poorly here even when their positive
pass-rate looks acceptable — which is the more honest measure of trust. Out of
scope for the first cut; add once the suite exists and has been run across the
benchmarked models.

## Non-determinism caveat

Negative scenarios are inherently flakier than positive ones — a borderline model
catches a false assert on some runs and misses it on others. That flakiness *is*
the signal (an oracle that only sometimes catches wrong answers isn't trustworthy),
so: keep the `INQUISITOR_LLM_IT` gate, don't wire these into a hard CI gate, and
read them as calibration. Strong models (the 31B; reasoning-on configs) should be
stable.

## Out of scope (possible follow-ups)

- Ergonomic-layer support for expected failure (e.g. `@Scenario(expect = FAIL)` or
  per-step expected verdicts in `inquisitor-harness-junit`). Deliberately deferred:
  it touches the core annotations/provider, whereas the standalone contract needs
  zero framework change. Revisit if negative scenarios prove worth first-class
  support.
- Automating the detection-rate metric into a report.

## Verification (when implemented)

1. `./gradlew build` green; the gated suite self-skips without a model.
2. `INQUISITOR_LLM_IT=true ./gradlew :inquisitor-demo:test --tests *NegativeScenarioTests`
   with the local model: all three negative tests pass (i.e. the model correctly
   caught each injected failure at the expected step) on a strong config (31B, or
   reasoning-on).
3. Optionally run across the benchmarked models to populate a detection-rate
   column.

## Docs to update

- `docs/roadmap.md` (new task row), `docs/decisions.md` (why oracle-calibration
  scenarios exist + the standalone-contract choice), `README.md` ("Verified models"
  — detection-rate axis, if adopted), this file (status → implemented).
