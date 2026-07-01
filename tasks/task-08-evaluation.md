# Task 08 — Credibility evaluation (`harness:evaluate`) — trustworthy-green + model report

A harness mode that runs the demo scenarios against a configured model and scores
how **credible** each step's PASS is — not by trusting the LLM's own verdict, but by
having a **second, independent LLM judge** the actor's claim against the *actual*
tool-call trace. It records a per-step credibility score, aggregates a suite-level
**credibility %**, and emits a report. The headline it can finally say honestly:

> 100% of steps passed — but credibility is only **85%**.

> Status: **in progress.** The core and the optional-module split have landed on
> `feature/evaluation`; the `harness:evaluate` Gradle task and the report are the
> remaining work (Phase C). Design decisions once marked **[DECIDE]** are resolved
> inline below.

## Why

The LLM is both the **actor** (drives the app under test) and the **judge** (emits
the verdict). A green run today means trusting the judge — and the judge has been
caught lying: `gemma-4-12B` (reasoning off) skips an import call and rubber-stamps
the step; `GLM-4.7-Flash` (reasoning off) fabricates whole responses. The only way
to trust green is to verify against a source that **isn't the actor**.

That source is the **tool-call trace** — the real results the tools returned,
captured out-of-band — and a **separate evaluator model** that checks the actor's
claim against it. Spring AI ships this natively (the
[`Evaluator`](https://docs.spring.io/spring-ai/reference/api/testing.html) interface,
`FactCheckingEvaluator` / `RelevancyEvaluator`), so we lean on the framework instead
of hand-rolling fuzzy string matching.

This is complementary to [task-07](task-07-fault-detection.md):

- **task-07** — does the *judge* catch a deliberately wrong assertion? (specificity)
- **task-08** — is the *actor's* green verdict *earned* — did it really do the work,
  and does its claim hold up against the real trace? (anti-hallucination / ground truth)

They share one piece of infrastructure — a structured record of every tool call —
**built once and reused by both**.

Bonus payoff: this populates the README "Verified models" table (model ×
quantization × reasoning → passed %, **credibility %**, duration — plus a future
detection-% column) with *measured* data, and is a real consumer feature: score *your*
model choice against *your* scenarios before trusting it.

## The trust model (read this first — it's the crux)

A second LLM only counts as independent verification if it is **grounded on something
the actor cannot fabricate**. That something is the tool-call trace.

- **Grounded on the conversation → theatre.** The conversation history *contains the
  actor's fabricated tool responses*. A judge reading the conversation sees a claim
  consistent with the invented response and blesses it. Both lie, in agreement.
- **Grounded on the real trace → a real check.** The evaluator's context is the
  out-of-band `ToolCallRecord` ledger. Now "model claims it imported 5 rows" can be
  checked against "is there actually a `POST /accounts/import` in this trace?" — and a
  skipped-call rubber-stamp surfaces.

So the ledger is **not** replaced by the evaluator — its role *shifts*: it stops being
"the checks" and becomes **the evidence the judge is grounded on**. Two corollaries,
both trust-critical:

1. **The evaluator must be grounded on the real ledger**, never the conversation.
2. **The evaluator model must be separately configurable, and should be a *different*
   model** (ideally stronger / different family). A model judging itself shares its own
   failure modes and will rubber-stamp its own fabrication — document a self-judge as
   the weakest configuration.

The credibility score is **non-deterministic** and read as **calibration, not a hard
gate** — same alignment as task-07. The deterministic gates below stay cheap and free.

## Goals

- A `EvaluationStepRunner` decorator that, after each step, scores the
  actor's verdict against the real tool trace using a Spring AI `Evaluator` backed by a
  separate model, and records the score.
- Tool-call capture as typed `ToolCallRecord`s, scoped per step, with **no change to the
  harness's public behaviour** (capture is additive and only wired when evaluation is on).
- Deterministic gates kept alongside the score (no skipped steps; all verdicts PASS).
- A suite-level **credibility %** plus per-step detail, emitted as a report.
- Delivered as a Gradle **`harness` task group** with an **`evaluate`** task; the
  consumer-facing shape (prototype in the demo build first, extract to a plugin).
- Reuse the same tool-call ledger that task-07's fault suite can later use for a
  (still-unbuilt) detection-% column.

## Design

**Module layout.** Evaluation ships as two optional modules mirroring the OpenAPI
plugin: **`inquisitor-harness-evaluation`** (the judge — `StepEvaluator`,
`EvaluationCategory`, `StepEvaluationResult`/`Record`, `StepEvaluationRecorder`,
`EvaluationStepRunner` — plus `RecordingToolCallback` and `EvaluationProperties`) and
**`inquisitor-harness-evaluation-starter`** (`InquisitorEvaluationAutoConfiguration`). The
harness core keeps only the trace *seam* — `StepRunner`/`StepRun`, `ToolCallRecord`, and
`TraceKeys` (the `ToolContext` ledger key); `LlmStepRunner` threads the ledger
unconditionally. The core references nothing in the evaluation modules, so dropping both
leaves a working, unevaluated harness (the OpenAPI-grade removal bar).

### 1. Rename the step seam: `StepRunner` (frees "Evaluator" for scoring)

Today's `StepEvaluator` / `ChatClientStepEvaluator` collide with Spring AI's `Evaluator`
once scoring enters. Rename the *driving* seam so "Evaluator" means *scoring only*:

| today | becomes |
|-------|---------|
| `StepEvaluator` (interface) | **`StepRunner`** |
| `ChatClientStepEvaluator` | **`LlmStepRunner`** |
| — | **`EvaluationStepRunner`** (decorator) |

The runner returns the verdict **plus the trace it produced**, so the decorator reads
the trace straight off the return value — no global state:

```java
interface StepRunner {
    StepRun run(StepRequest request);
}

record StepRequest(String conversationId, String userMessage, Scenario scenario, Step step) {}
record StepRun(StepVerdict verdict, List<ToolCallRecord> toolCalls) {}
```

`StepRequest` also resolves the earlier "expose the userMessage" point: the message is
built once (the "step 1 prepends the scenario description" logic moves into a small
helper / `ScenarioEvaluation`), and is then right there for the evaluator's `userText`.
`ScenarioEvaluation` keeps timing the call as it does now.

### 2. Tool-call capture via `ToolContext` (not a ThreadLocal, not `ToolCallingAdvisor`)

```
ToolCallRecord(toolName, arguments, result, order, elapsed)
```

All of a step's tool calls happen *inside* one `chatClient…call()` (Spring AI runs the
tool loop synchronously within it). Capture must be scoped to that call and surfaced to
the decorator outside it. The elegant channel is Spring AI's **`ToolContext`** — the
per-call `Map` the framework threads to every `ToolCallback.call(input, toolContext)`:

- **`LlmStepRunner` owns a fresh per-call ledger** and threads it through `ToolContext`:

  ```java
  public StepRun run(StepRequest req) {
      val ledger = Collections.synchronizedList(new ArrayList<ToolCallRecord>());
      val verdict = chatClient.prompt()
          .user(req.userMessage())
          .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, req.conversationId()))
          .toolContext(Map.of(TraceKeys.LEDGER, ledger))   // ← the channel
          .call()
          .entity(StepVerdict.class);
      return new StepRun(verdictOrFail(verdict), List.copyOf(ledger));
  }
  ```

- **`RecordingToolCallback`** wraps each registered `ToolCallback`, reads the ledger from
  the `ToolContext`, and appends a record around the delegate call. The underlying
  `@Tool` methods on `HttpRequestTool` / `SqlTool` never learn about any of it:

  ```java
  @Override public String call(String input, @Nullable ToolContext ctx) {
      val ledger = ctx == null ? null
          : (List<ToolCallRecord>) ctx.getContext().get(TraceKeys.LEDGER);
      val start = System.nanoTime();
      val result = delegate.call(input, ctx);
      if (ledger != null) {
          ledger.add(new ToolCallRecord(getToolDefinition().name(), input, result,
              ledger.size(), Duration.ofNanos(System.nanoTime() - start)));
      }
      return result;
  }
  ```

Per-call ledger instance ⇒ thread-safe under parallel tool execution *and* parallel
scenarios — no ThreadLocal side-channel, no log parsing, no global bean for the trace.
The wrapping lives in the optional evaluation starter — a `BeanPostProcessor` that
decorates every `ToolCallback` bean with `RecordingToolCallback` when
`inquisitor.harness.evaluation.enabled=true` — so the core knows nothing about it and a
normal run has zero wrapping and zero overhead.

**Why not `ToolCallingAdvisor`?** It *relocates* tool execution into the advisor chain so
you can *control* it (retry, approve, short-circuit, mutate). We only want to *observe*.
`ToolContext` is the lighter, purpose-built fit. Keep the advisor in our back pocket for
the day we want to *act* on calls (e.g. a hard inline "this required call must happen"
gate, or tool-layer fault injection).

**Keep `ToolCallRecord` structured, render to text at the boundary.** Structured costs
nothing, renders trivially to the transcript the judge sees, and stays reusable for
per-call report detail. A bare string is lossy.

### 3. The credibility evaluator (Spring AI `Evaluator`, grounded on the real trace)

`EvaluationStepRunner` is a transparent decorator: it returns the actor's
verdict untouched and only records a score on the side.

```java
public StepRun run(StepRequest req) {
    val run = delegate.run(req);                        // verdict + real trace
    val eval = evaluator.evaluate(new EvaluationRequest(
        req.userMessage(),                              // userText  = the step instruction
        toDocuments(run.toolCalls()),                   // context   = the REAL trace
        renderVerdict(run.verdict())));                 // response  = outcome + reasoning + evidence
    recorder.record(req.scenario(), req.step(), eval.getScore(), eval.getFeedback());
    return run;                                         // verdict untouched
}
```

**Decided — a custom-prompted `Evaluator` on the fact-checking shape, not
`RelevancyEvaluator`.** Relevancy only asks "is the answer on-topic for the question" — a
rubber-stamp ("I imported all 5 rows") is perfectly relevant and perfectly wrong.
Fact-checking asks "is the claim supported by the context," which, grounded on the trace,
is exactly the question. We supply our own prompt (next) so the judge explicitly confirms
the *required actions appear in the trace*, not just that the prose is consistent.

**Decided — evaluator model wiring** is a separate `inquisitor.harness.evaluation.model`
(+ baseUrl / key) independent of the actor model. Default off; document the self-judge
caveat (§"trust model").

### 3a. The scoring prompt and categories

The prompt's whole job is to make the judge **audit, not re-test**. Three principles drive
every word of it:

1. **The trace is the sole ground truth, and it is *complete*.** The judge is told these
   are *all* the actor's calls — otherwise absence means nothing and it can't catch
   "missing work."
2. **The actor's reasoning/evidence is a *claim to verify*, never evidence in itself.**
   This is the line that stops the judge inheriting the actor's fabrication.
3. **The judge has no tools.** It rules on the trace alone — temperature 0, structured
   output.

**Categorical scoring, mapped to a score in our code.** Asking a (possibly local) judge
for a free-form `0.0–1.0` invites mushy, unstable numbers; a discrete classification is
far more reliable and keeps the numeric mapping under our control:

| category | meaning | score |
|---|---|---|
| `GROUNDED` | every action and value the verdict relies on is present in and consistent with the trace | 1.0 |
| `PARTIALLY_GROUNDED` | verdict plausible, but a cited value or required action is missing from the trace | 0.5 |
| `UNSUPPORTED` | PASS/FAIL asserted with no tool result that entails it (e.g. "balance correct" with no read) | 0.2 |
| `CONTRADICTED` | the trace contradicts a cited value/action, or claims an action that never happened | 0.0 |

Suite credibility % = mean of the per-step scores.

```
You are a test-trace auditor. A test agent performed ONE step against an
application and reported a verdict. Your job is NOT to re-run or re-judge the
step — it is to decide whether the agent's reported verdict is SUPPORTED by
what the tools actually did.

The TOOL TRACE below is the SOLE ground truth. It is the COMPLETE record of
every tool call the agent made during this step and exactly what each returned.
If an action is not in the trace, it did not happen. The agent's REASONING and
EVIDENCE are CLAIMS to be verified against the trace — they are never evidence
in themselves.

STEP INSTRUCTION:
{userText}

TOOL TRACE (ground truth — every call made and its real result):
{context}

AGENT'S REPORTED VERDICT (the claim you must audit):
{response}

Audit the claim against the trace. Look for, in order of severity:
  1. CONTRADICTION — a cited status, id, count, amount or action that the trace
     contradicts, or a claimed action with no matching tool call.
  2. MISSING WORK — an action the step required (a write, an import, a re-read
     to verify the result) that is absent from the trace.
  3. UNSUPPORTED VERDICT — PASS/FAIL asserted without a tool result that
     entails it (e.g. "the balance is correct" with no query that read it).

Classify the verdict as exactly one of:
  GROUNDED            — every action and value it relies on is in, and consistent
                        with, the trace.
  PARTIALLY_GROUNDED  — plausible, but a cited value or required action is missing.
  UNSUPPORTED         — the verdict is not entailed by any tool result.
  CONTRADICTED        — the trace contradicts it, or a claimed action never happened.

Respond ONLY as JSON:
{
  "category": "GROUNDED | PARTIALLY_GROUNDED | UNSUPPORTED | CONTRADICTED",
  "findings": ["<one short note per issue: the claim, and what the trace shows>"]
}
```

Why this shape over the alternatives:

- **vs. relevancy's default prompt** — relevancy passes a rubber-stamp trivially; this
  asks whether the claim is *entailed by the evidence*, grounded on the trace.
- **vs. a raw 0–1 score** — unanchored numerics drift run to run; four labeled anchors give
  the judge a discrete, repeatable decision and keep the score math deterministic.
- **vs. per-claim decomposition** ("list every atomic claim, verify each, score =
  supported/total") — more rigorous but leans hard on the judge's reasoning and is the most
  variable on weaker models. Keep it as a **v2** for a stronger evaluator model; ship the
  categorical version first.

Two implementation details:

- **Render the trace deterministically** as `{context}` — one line per call,
  `#order toolName(args) → result`, results truncated to a sane budget. That rendering *is*
  the `toDocuments(toolCalls)` step.
- **The actor's `evidence` list is the highest-value part of `{response}`** — it is the
  actor's own pointer at "the tool responses I relied on," so the judge's sharpest check is
  "do those evidence strings actually appear in the trace?"

### 4. The deterministic gates (free)

Two checks need no LLM — keep them as hard gates beside the score:

1. **No skipped steps** — `result.results().size() == scenario.steps().size()`.
2. **All verdicts PASS** — every `StepVerdict.outcome() == PASS` (positive suite).

"Truly passed" = both gates hold; **credibility %** = the evaluator's aggregate score
over the steps. A run can be 100% passed / 85% credible — that gap is the headline.

### 5. Recorder + report

- **`StepEvaluationRecorder`** — a shared output sink (concurrent, keyed by scenario × step)
  collecting `(score, feedback)`; the only cross-step shared state. Aggregates to a
  suite-level credibility %.
- **Console summary** + a written artifact in **both** formats: JSON as the artifact of
  record, Markdown rendered from it so the README "Verified models" table is *generated*,
  not hand-pasted.
- Columns: model, quantization, reasoning, **passed %** (deterministic gates),
  **credibility %** (evaluator aggregate), **duration**, plus per-step detail (score,
  feedback, tool calls, per-step timing). A **detection %** column is left for the future
  (task-07's deferred metric).

### 6. Gradle surface — `harness:evaluate` (demo task first, plugin second)

The real mechanism lives in the optional **`inquisitor-harness-evaluation-starter`**, not
the Gradle plugin: a flag (`inquisitor.harness.evaluation.enabled=true`) gates its
autoconfig, which **decorates each `ToolCallback` bean with `RecordingToolCallback`** (a
`BeanPostProcessor`) and **wraps the actor `LlmStepRunner` with `EvaluationStepRunner`** (a
`@Primary StepRunner`). The Gradle task is sugar on top: it sets the flag + the
evaluator-model env, runs the existing JUnit suite, then renders the report.

```bash
# single config — individual -P params:
./gradlew :inquisitor-demo:evaluate -Pmodel=gemma-4-31B-it-QAT-Q4_0 -Preasoning=off
# multi-config run from a file (emits the whole table in one pass):
./gradlew :inquisitor-demo:evaluate -Pbenchmark=benchmark.yml
```

- **First cut:** a task in the `:inquisitor-demo` build (group `harness`, name `evaluate`)
  driving a JUnit `Launcher` over a dedicated suite, gated like the other LLM suites — it
  reuses the entire context wiring (RANDOM_PORT app + harness) already in place.
- **Then extract** an `io.inquisitor.harness` Gradle plugin that registers the `harness`
  group + `evaluate` task for *consumers*, once the bean-swap + report shape are proven —
  same "demo first, plugin second" sequencing task-07 / the original plan used.
- **Decided — two input modes:** individual `-P` params (`-Pmodel`, `-PbaseUrl`,
  `-Preasoning`, …) for a single config; or a checked-in `benchmark.yml` listing several
  configs for a multi-config run that emits the whole table in one pass. Single-config is
  the default; the `benchmark.yml` run is opt-in (`-Pbenchmark=…`).

## Hard parts / risks (call out honestly)

- **A judge can rubber-stamp too** — mitigated only by grounding on the real trace *and*
  using a different/stronger evaluator model. A self-judge is the weakest config; say so.
- **Non-determinism** — credibility scores vary run to run; read as calibration, keep the
  `INQUISITOR_LLM_IT` gate, don't wire into a hard CI gate. Consider N repeats for a
  stability number.
- **Cost** — evaluation doubles LLM calls per step (act + judge), though the judge is a
  single shot with no tool loop, so cheaper. A multi-config `benchmark.yml` run is long —
  single-config default, the file run opt-in. A future detection-% column would be a
  *second* full pass (bug-injected context) — keep it a separate opt-in, not bundled.

## Out of scope (possible follow-ups)

- A published Gradle plugin for consumers to evaluate their own suites (the v2 once the
  demo task proves the shape).
- A hard inline required-call gate via `ToolCallingAdvisor` (control, not just observe).
- CI integration / credibility trend tracking across commits.

## Relationship to task-07

Build the **tool-call ledger** (§2) first; both tasks consume it. task-07 asserts a FAIL
is correctly produced; task-08 scores whether a PASS is *earned*. task-07's detection-rate
metric was **deferred (never built)**; task-08's ledger + report infra makes it cheap to
add later as an opt-in second pass that populates a detection-% column — out of scope here.

## Verification (when implemented)

1. `./gradlew build` unaffected — evaluation is off by default; the gated runner self-skips
   without a model; a normal run does no tool wrapping.
2. `:inquisitor-demo:evaluate -Pmodel=<31B> -Preasoning=off` with a *separate* evaluator
   model → all scenarios pass the deterministic gates and score high credibility; report
   shows per-step scores + full tool-call detail.
3. Re-run against `gemma-4-12B -Preasoning=off` → the grounded evaluator **drops the
   credibility score** on the import scenario (claims an import the trace doesn't contain)
   even though the actor's verdict claimed PASS — i.e. it surfaces the false positive we
   already know exists.
4. The multi-config `benchmark.yml` run regenerates the README "Verified models" table
   from measured data.

## Docs to update

- `docs/roadmap.md` (task row), `docs/decisions.md` (why a second-LLM credibility check
  grounded on the real trace; `ToolContext` capture over ThreadLocal / `ToolCallingAdvisor`;
  the `StepRunner` rename; evaluator-model independence; the optional-module split with the
  core keeping only the trace seam and the starter decorating `ToolCallback` beans via a
  `BeanPostProcessor`), `README.md` (generated
  verified-models table with the credibility column; `harness:evaluate` usage),
  `CLAUDE.md` (build commands + the `StepRunner` rename), this file (status → implemented).
