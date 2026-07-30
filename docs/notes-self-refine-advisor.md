# Notes: self-refine evaluation advisor

Status: **idea, not scheduled.** Captured 2026-07-16 from a design discussion.
Not a task — revisit when we decide to harden the runner.

## The idea

Spring AI's LLM-as-judge guide describes a `SelfRefineEvaluationAdvisor`
([docs](https://docs.spring.io/spring-ai/reference/guides/llm-as-judge.html)):
a `CallAdvisor` that loops **generate → judge (1–4 scale) → if below
`successRating` and under `maxRepeatAttempts`, splice the judge's feedback into
the user message and retry; else return the last response.**

Applied to Inquisitor, the goal would be to **improve the actor as a test
*runner*** — eliminate two failure classes at execution time rather than only
scoring them after the fact:

1. **Wrong tool interactions** — e.g. the actor backslash-escaped a JSON body,
   the app returned a spurious 400, and the rule under test was never exercised.
2. **Ungrounded assertions** — PASS without the supporting read, a hallucinated
   value.

## The core tension (do not lose this)

Inquisitor is a **measurement** tool. The evaluation score *is* the product — it
measures how trustworthy the actor is as an oracle. Self-refine improves the
actor's output at the cost of measuring it honestly:

- **Case A — mechanical botch** (malformed request → rejected → honest FAIL, but
  the test never ran the real path). Refinement is genuinely valuable here.
- **Case B — fake verdict** (PASS without the read). This is *exactly what the
  evaluation exists to expose*. Silently coaching the actor into doing the read
  erases the evidence that it's unreliable. In fault-detection calibration this
  is catastrophic: the mutation-detection rate would then measure
  "actor + judge coaching," not the actor, and would silently inflate.

**Resolution:** self-refine is allowed to change the *test outcome*, but the
**pre-refinement verdict and a refinement-round count must be recorded** so the
score still reflects "got it right first try." Decided:
**record the pre-refinement verdict; the refined result is the reported test
outcome.** The round count is itself a quality signal (a step that needed 2
rounds is a weaker result than a clean one).

## Non-idempotency: narrower than it first looks

Spring's advisor sees the `ChatClientResponse` **after** tools have already
executed (default `internalToolExecutionEnabled=true`); the weather demo is safe
only because its tool is a read. So a plain response-level advisor is inherently
*post-side-effect*. But decomposed by failure, most retries are provably safe:

| Failure | Corrective action | Idempotent? |
|---|---|---|
| Wrong tool interaction (malformed body → 400) | resend corrected request | ✅ app *rejected* the first — no state changed |
| Ungrounded: missing read | issue the read (GET/SELECT) | ✅ reads are idempotent |
| Ungrounded: contradicted by trace | flip the verdict — no tool call | ✅ pure re-judgment |
| Ungrounded: missing write | do the write, once | ✅ hadn't run yet |
| Successful write, then a bad verdict about it | never re-run the write | ❌ the only trap |

Key property: **a wrong interaction the app rejected (4xx/5xx/parse error) left
no side effect, so retrying it is inherently safe.** The only dangerous move is
re-running a step whose write already succeeded — and you never need to: fixing a
bad verdict about a successful write needs a *read* or a *re-judgment*, not a
repeat of the write.

## The design move that makes it safe: continue, don't restart

The trap only springs if the loop **discards the conversation and re-runs the
step from scratch**. Instead, append the judge's feedback to the actor's
*existing* message history and let it take a *corrective* next action. The actor
remembers it already transferred, so it won't re-POST — it'll do the missing GET
or resend the bounced request.

Implementation consequence: today `EvaluationStepRunner` wraps `LlmStepRunner`
and judges *after* it returns; re-calling `llmStepRunner` fresh = restart = the
trap. So **`LlmStepRunner` must become resumable** — support continuing the same
conversation with appended feedback, with the loop inside it. That resumability
is the real plumbing cost, not the advisor logic.

Residual risk: a weak local model might still blindly re-issue a side-effecting
call. Mitigation — **gate refinement on "the corrective action is provably
idempotent":** offer a retry only when the last interaction was rejected
(4xx/5xx/parse error) or the fix is a read / re-judgment. If the fix would
require repeating a succeeded write, don't refine — record it as a finding.

## Two mechanisms, not one

The two goals want different machinery; only the second is really "LLM
self-refine":

- **Wrong tool interactions** are mostly **deterministically detectable** (parse
  error 400, tool exception, 405) — no judge LLM needed. This is the `UNSOUND`
  trace-soundness check sketched under task-08 discussions: detect a malformed
  interaction and bounce it back with a fixed message ("resend as raw JSON").
  Pre-flight validation, cheaper and faster than a model round-trip.
- **Ungrounded assertions** are **semantic** — they need the judge, and they
  operate on the verdict/reads, which is the naturally idempotent part.

Reserve the LLM refine loop for groundedness; use the deterministic soundness
gate for the mechanical failures.

## Alternative worth remembering

`internalToolExecutionEnabled(false)` lets you intercept the model's *proposed*
tool calls before they fire — evaluate the *plan* pre-flight (could catch a
malformed body before sending). But it means taking over the whole tool-calling
loop, and evaluating intent is weaker than evaluating results (a plausible call
can still fail against the real app). Heavier redesign; note but don't reach for
it first.

## Also keep in mind

- Low attempt cap (**1–2**, not the guide's 15): slow local models, and a weak
  MoE judge means a high cap invites the actor to fit the judge's blind spots.
- Cost/latency compounds per step across a whole suite.
- The escaped-JSON body case that motivated this was already mitigated cheaply
  with actor-prompt guidance (commit `ee11206`); self-refine would be the
  belt-and-braces recovery when prompt guidance isn't enough.

## Related

- [[project_oracle_rubber_stamps]] — the fake-PASS failure mode this must not launder.
- `docs/decisions.md` — the judge-independence rationale.
- task-08 evaluation notes — the `UNSOUND` trace-soundness check.
