# Task 07 — Fault detection (oracle calibration via mutation testing)

A suite that verifies the model correctly reports `FAIL` when the application
under test is **genuinely defective**. Where the positive suite measures
*sensitivity* (a good run isn't wrongly failed), this measures *specificity* —
that the oracle catches a wrong answer instead of rubber-stamping it.

This is the harness's most important safety property: an LLM judge's scariest
failure mode is the **false positive** (a green verdict on a step it never truly
verified). We've already observed it — `gemma-4-12B` (reasoning off) skips an
import call and rubber-stamps the step; `GLM-4.7-Flash` (reasoning off) fabricates
responses for whole scenarios. This task turns "the model might rubber-stamp" from
a caveat in the README into a measured number.

> Status: **Phase 1 implemented** and green on the local model — the router
> (`AccountService` interface + `AccountServiceImpl` + `BuggyAccountServiceImpl` +
> `@Primary AccountServiceRouter`), `FaultDetectionTests`, and the flattened scenario
> tree are in. **Phase 2** (fault detection through the `@Harness` layer) is still
> planned. (Supersedes two earlier drafts: the original "false-expectation" fixtures,
> then a profile-swapped buggy service. See *Why mutation* and *Why a router* below.)

## The core principle: correct test, buggy system

A fault-detection scenario must be a **faithful, logically-sound test that fails
because the system under test has a real bug** — *not* a self-contradictory test
that fails by construction.

- **Avoid (false expectation):** deposit `100`, then assert the balance is `50`.
  The app is correct; the *expectation* is nonsense. It "fails," but only because
  the test contradicts itself.
- **Want (seeded bug):** deposit `100`, then assert the balance is `100` — the
  expectation a competent engineer would actually write. It fails only because the
  app has a **seeded defect** that leaves the balance wrong (e.g. `0`, or `99.00`).

### Why mutation, not false expectations

Mechanically the oracle does the same thing either way: observe reality, compare to
the stated expectation, report a mismatch. The difference is *where the anomaly
sits and what it tempts the model to do*:

- A **false expectation** puts the anomaly in the *instructions*. An
  instruction-following model can smell that `expect 50` is malformed and either
  flag the test as broken or "helpfully" reinterpret it to `100` and pass — so a
  PASS is ambiguous (rubber-stamp, or correct rejection of a nonsense test?). That
  measures the model's tolerance for incoherent prompts, which is not the job.
- A **seeded bug** puts the anomaly in the *system*. The expectation is impeccable,
  so the model has no escape hatch: it must actually observe the buggy value and
  report that it didn't match a perfectly reasonable expectation. That is the real
  production job of an oracle — **good tests, buggy code** — and a rubber-stamper
  has nowhere to hide.

This is **mutation testing applied to the oracle**: seed a realistic defect (a
"mutant"), run a correct scenario, and verify the oracle *kills* the mutant
(reports `FAIL` at the right step).

### A consequence: reuse the positive scenarios — there is no negative suite

Once the bug lives in the system and not the markdown, a fault-detection scenario is
**byte-identical to a positive one**. So we don't author negative fixtures at all —
we **reuse the existing positive scenarios** and run them against a seeded bug. This
is exactly how real mutation testing works: you don't write special tests for
mutants, you check that your *existing* suite kills them.

Therefore:

- **Delete the `negative/` tree** (`scenarios/negative/{explicit,cucumber}`). It no
  longer has a reason to exist and the negative/positive split was confusing.
- **Flatten** `scenarios/positive/{explicit,cucumber,intent}` →
  `scenarios/{explicit,cucumber,intent}` (the `positive/` qualifier is now
  redundant). Update each suite's `scenarioDir`: `ScenarioTests` (standalone),
  `PositiveScenarioSuite` subclasses (`ExplicitScenarioSuiteTest`,
  `CucumberScenarioSuiteTest`), and `IntentScenarioSuiteTest`. Mechanical.
- The "this scenario should fail under bug X at step Y" knowledge lives in the
  **fault-detection test code**, not in any markdown.

## Design

Implemented in two phases: **Phase 1** — the router + standalone `FaultDetectionTests`
+ the scenario-tree flatten (this task's core, build first). **Phase 2** — fault
detection through the ergonomic `@Harness` layer (deferred; see below).

### The fault router

Mutants are switched at **runtime through a router**, not via Spring profiles.
A profile costs a fresh Spring context per mutant set and can't switch mid-suite;
a router lives in **one context** and flips per test case — true one-bug-at-a-time
isolation with no context proliferation.

Shape:

- **`AccountService`** — extract an interface from today's concrete service
  (mechanical; `src/main`).
- **`AccountServiceImpl implements AccountService`** — the correct service, the only
  bean production and the positive suites ever see (`src/main`, `@Service`).
- **`AccountServiceRouter implements AccountService`** — `@Primary`, lives in
  `src/test`, contributed **only** to the fault-detection suite's context. Holds the
  correct impl plus one buggy impl per bug and an `EnumSet<Bug>` of what's active;
  each method delegates to the buggy impl iff its bug is enabled, else to the correct
  impl. API:

  ```java
  router.enableBug(Bug.DEPOSIT_NOT_PERSISTED);
  router.disableBug(Bug.DEPOSIT_NOT_PERSISTED);
  router.disableAllBugs();   // reset between cases
  ```

**Keep the delegates Spring-managed.** The service methods are `@Transactional`,
which only works through a Spring proxy, so the correct impl and each buggy impl
must be **beans** (transactionally proxied); the router just *selects* which bean to
call. A buggy impl can `extends AccountServiceImpl` and override only the mutated
method to minimise duplication. (A plain `new BuggyImpl(...)` inside the router would
silently drop `@Transactional`.)

**Placement — `src/test`, for demo clarity (not production safety).** The demo is
not a shipped artifact — only the harness modules are "production" — so there is no
*safety* reason the buggy impls must leave the demo's main source. The reason to put
the router + buggy impls in `src/test` (contributed only where the fault suite
imports them) is to keep the demo's *main* source readable as the **reference
consumer**, which is its whole purpose: a reader studying the demo as an example
shouldn't trip over fault-injection plumbing. `bootRun` and the positive suites then
get the plain `AccountServiceImpl` with no router in their path. If you'd rather, the
machinery can live in `src/main` behind the dormant-by-default router — the demo
isn't published either way; this is a clarity call, not a correctness one.

### Test class — `FaultDetectionTests` (standalone)

Mirrors `ScenarioTests` (standalone harness: `@Autowired ScenarioExecutor`/
`ScenarioParser`/`HttpTargetRegistry`, `@LocalServerPort`, app target registered in
`@BeforeEach`), gated with `@EnabledIfEnvironmentVariable(INQUISITOR_LLM_IT=true)`,
and `@Autowired AccountServiceRouter router`. No profile needed — the router is a
test bean in this context and defaults to no bugs.

The standalone contract is the right home for the first cut because control is
trivial and explicit: enable the bug, run the *existing* positive scenario, assert
the failure landed at the step the bug manifests at, then reset.

```java
@AfterEach void clearBugs() { router.disableAllBugs(); }

private void runExpectingFailureAt(Bug bug, String classpathLocation,
                                   String expectedStepTitleFragment) {
    router.enableBug(bug);

    val resource = new ClassPathResource(classpathLocation);
    val scenario = parser.parse(read(resource), resource.getFilename());
    val result = executor.evaluate(scenario);

    // The model must NOT have passed everything — that would be a false positive
    // (it failed to notice the seeded bug).
    val failure = result.firstFailure();
    assertThat(failure)
            .withFailMessage(() -> "Expected the model to catch " + bug + " at a step containing \""
                    + expectedStepTitleFragment + "\", but the scenario passed:\n" + describe(result))
            .isPresent();

    // …and it must fail at the step the bug manifests at, not somewhere upstream.
    assertThat(failure.get().step().title())
            .withFailMessage(() -> "Scenario failed at the wrong step:\n" + describe(result))
            .containsIgnoringCase(expectedStepTitleFragment);
}
```

`describe(...)` is the verdict-dump helper already in `ScenarioTests` (lift it to a
shared test util, or duplicate — it's small). Reusing it means a failure message
shows the model's reasoning/evidence, exactly what you want when diagnosing a false
positive.

Note on `firstFailure()`: with fail-fast, `results` holds every step up to and
including the first failure, so the steps before the buggy one are confirmed to have
genuinely passed (asserting the break is at step N implicitly checks 1..N-1 passed).

### The bugs and the scenarios that should kill them

Each `Bug` mutates one endpoint; each reused positive scenario already exercises that
endpoint and asserts on the value the bug corrupts.

| `Bug` | Mutation | Scenario reused | Expected fail step |
|-------|----------|-----------------|--------------------|
| `DEPOSIT_NOT_PERSISTED` | `deposit` records the txn but never updates the balance → stays `0` | `open-account-and-deposit` | the balance-check step |
| `TRANSFER_CREDIT_DROPPED` | `transfer` debits the source but doesn't credit the destination (or shorts it) | `transfer-between-accounts` | the reconciliation step |
| `WRONG_CURRENCY` *(or `IMPORT_ROW_DROPPED`)* | `createAccount` persists/returns the wrong currency; or `importAccounts` silently drops one row (count = N−1) | `open-account-and-deposit` / `import-accounts` | the currency / count assertion |

These map onto the old gross / off-by-one / subtle-field shapes — gross
rubber-stamping, numeric discrimination, and a single quiet field a skimming model
misses.

### Phase 2 — fault detection through the `@Harness` layer (deferred)

JUnit *can* toggle a bug per test method — you key on a **method annotation**, not
the test name:

- `@EnableBug(Bug.DEPOSIT_NOT_PERSISTED)` on a `@Scenario` method.
- An extension implementing `BeforeEachCallback`/`AfterEachCallback`:
  `beforeEach(ExtensionContext)` reads the annotation off `getRequiredTestMethod()`,
  pulls the router via `SpringExtension.getApplicationContext(ctx).getBean(...)`, and
  calls `enableBug`; `afterEach` calls `disableAllBugs()`.

This is deferred to Phase 2, because the `@Scenario` template asserts each step
*passes*; using it for fault detection also needs expected-failure verdicts
(`@Scenario(expect = FAIL)`), which touches the core template provider. So the
ergonomic layer needs **two** new pieces (`@EnableBug` + expected-fail) where the
standalone contract needs **zero** — which is why Phase 1 ships on the standalone
contract alone, and `@EnableBug` waits until fault scenarios are wanted in the
`@Harness` suite.

## Optional: detection-rate metric for the model table

A second axis for the README "Verified models" table beyond runtime and positive
pass-rate: **detection rate** = % of seeded bugs the model correctly caught. `*-off`
configs that fabricate/skip will likely score poorly here even when their positive
pass-rate looks fine — the more honest measure of trust. Out of scope for the first
cut; add once the suite exists and has run across the benchmarked models.

## Non-determinism caveat

Fault-detection runs are inherently flakier than positive ones — a borderline model
catches a seeded bug on some runs and misses it on others. That flakiness *is* the
signal (an oracle that only sometimes catches a real bug isn't trustworthy), so:
keep the `INQUISITOR_LLM_IT` gate, don't wire these into a hard CI gate, and read
them as calibration. Strong models (the 31B; reasoning-on configs) should be stable.

## Out of scope (possible follow-ups)

- A larger bug catalogue (one per service method).
- Renaming `PositiveScenarioSuite` now that there's no negative counterpart — leave
  it; "positive" still contrasts with fault detection.
- Automating the detection-rate metric into a report.

## Verification (when implemented)

1. `./gradlew build` green; the gated suite self-skips without a model. The positive
   suites still pass against the flattened `scenarios/{explicit,cucumber,intent}`
   paths and never touch the router.
2. `INQUISITOR_LLM_IT=true ./gradlew :inquisitor-demo:test --tests *FaultDetectionTests`
   with the local model: every case fails at the expected step (i.e. the model caught
   each seeded bug) on a strong config (31B, or reasoning-on).
3. Mutants are real by construction: the *same* scenario files pass in the positive
   suites (no bug) and fail here (bug enabled), so the contrast itself proves the
   failure comes from the seeded bug, not a bad scenario.
4. Optionally run across the benchmarked models to populate a detection-rate column.

## Docs to update

- `docs/roadmap.md` (task row), `docs/decisions.md` (why oracle-calibration exists +
  the **mutation-over-false-expectation** and **router-over-profile** choices + the
  flattened scenario layout), `README.md` ("Verified models" — detection-rate axis,
  if adopted), this file (status → implemented).
