# Task 16 — Supervised step refinement

Implemented on `feature/supervised-step-refinement`.

The evaluation starter selects `SupervisedStepRunner` when
`inquisitor.harness.evaluation.refinement.enabled=true`. Observation-only
evaluation is a separate opt-in. `max-corrections` defaults to two additional
actor turns, shared by format repair and semantic correction.
The starter selects one clean mode: `evaluation.enabled` creates the scoring
`Evaluator`, while `refinement.enabled` creates the `StepReviewer` and uses its
final decision as the evaluation result. Refinement takes precedence if both are
true; it does not create or invoke the observation-only evaluation step.
Supervised completion is not translated into legacy evaluation callbacks, so it
does not emit an additional evaluation/score event.

## Execution contract

- `LlmStepRunner.Conversation` owns the current step's cumulative ledger and
  immutable actor attempts. Continuations use the same actor client and scenario
  memory, explicitly including the recorded trace in the correction prompt.
- Format validation runs locally. Valid verdicts go to `StepReviewer`, whose
  default implementation uses the configured, tool-free evaluation model.
- ACCEPT approves supported PASS or FAIL; CORRECT supplies actionable feedback;
  STOP ends an execution that continuation cannot repair faithfully.
- Corrective writes are allowed. The reviewer must preserve scenario assertions,
  order, and observed defects. Fresh fixtures and automatic restarts are deferred.
- Review happens after a completed actor turn. The correction budget does not
  cap the inner Spring AI tool loop or allow the reviewer to interrupt it.
- Exceptions and unknown effects do not authorize replay. Actor/tool or reviewer
  failures, STOP, and budget exhaustion produce `UnresolvedStepException`.
  JUnit's existing abort path prevents this from counting as an expected defect.
- Tool recording covers callbacks and callback providers, including thrown calls.
  Concurrent invocations reserve unique ledger positions before execution.

## Reporting and compatibility

One step record contains all review rounds: raw answers, parsed verdicts or
conversion errors, new calls, review findings and decisions, and timings.
Reports distinguish first-attempt acceptance, acceptance after correction, and
unresolved runs. The final score is the team's assessment; prior scores remain
in the round history.

Core has no evaluation dependency. Existing constructors and callback
implementations remain supported. Both modes are disabled by default and can be
enabled independently. Runtime state stays local to each step conversation.

## Validation

Scripted actor/tool tests exercise missing API checks, missing truncation,
missing verification, malformed answers, preserving completed writes, accepting
application FAIL, STOP, shared correction limits, judge failures, and tool
exceptions. Additional tests cover reviewer prompt/response validation,
autoconfiguration, provider tools, JUnit expected failures, and all report formats.
Real-model runs are supplementary validation, not deterministic CI gates.

Validation on 2026-09-08: `./gradlew check` passed, and the evaluation-module
tests passed again after the final correction-budget guard. In a live smoke
test with supervision enabled, `accountNotFound` passed with first-attempt
acceptance. The seeded-fault `openAccountAndDeposit` run was stopped after ten
minutes: its deposit step remained inside the actor's tool loop without
returning a verdict to the reviewer. That fault run is inconclusive and does
not establish live recovery performance; it illustrates the per-turn
supervision limitation above.
