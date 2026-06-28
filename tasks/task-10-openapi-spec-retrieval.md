# Task 10 — OpenAPI context-size optimisation (digest + partial retrieval)

Stop injecting the **entire** raw OpenAPI document into every model call. For large,
production-grade APIs (hundreds of operations, deeply `$ref`-ed schemas) the raw YAML
is too big to ride in the system prompt — and because it's re-sent on **every**
round-trip via chat memory, the cost is `spec_size × N_inferences`. Two complementary
levers, both **size-gated** so small specs keep today's behaviour:

- **Shrink everything** — render the spec as a terse, faithful **digest** (compact
  operation signatures + a type dictionary) instead of raw YAML.
- **Select a subset** — surface only the operations relevant to the current step
  (a table-of-contents + on-demand lookup, or vector retrieval).

These compose (digest *is* the table of contents; a lookup returns a full slice for the
last-mile fidelity). Both are **modes of the existing OpenAPI plugin**, not a new
feature surface and not a core change. Called out as follow-ups in
[task-09](task-09-openapi-discovery.md) ("`$ref`-resolving digest" and "a per-endpoint
retrieval tool for very large APIs").

> Status: **planned** — design draft for review. No code yet.

## Why

`OpenApiAdvisor.before()` appends the whole YAML to the system message, and the spec
sits in chat memory, so it is re-sent on every step **and** every tool-call loop
within a step. For the demo (7 operations, ~217 lines) that's cheap and correct. For a
real API it blows the context budget on a 32k-ctx local model
([[project_harness_local_model]]) and wastes tokens proportional to step count. The
saving from sending only the relevant slice therefore **compounds** — this is where it
pays off, and only there.

## Goals

- **Size-gated** digest/retrieval modes in the existing `inquisitor-harness-openapi`
  plugin; **no core change** (the advisor-collection seam from task-09 already suffices).
- Below a configurable size threshold, behaviour is **byte-identical to task-09**
  (`full` mode): send the whole raw spec.
- A **deterministic digest** that renders the spec far smaller than raw YAML while
  staying **faithful** — no LLM in the loop (see "Why deterministic" below).
- For very large specs, the model is given only the operations it needs for the current
  step, each **self-contained** (its transitively-referenced schemas included).
- Correctness first: a step must still reach **any** endpoint it legitimately needs, and
  the description it reads must not silently drop fidelity (constraints, param location,
  response shape, status codes) — see Risks.

## Design

### A spec "slicer" (the shared, hard part — needed by digest *and* retrieval)

The retrieval unit is an **operation** (path + method): summary, description,
parameters, request body, responses. The non-trivial work is **`$ref` closure**: an
operation references `#/components/schemas/…`, so a slice must carry the *transitive
closure* of the schemas it touches, or the model sees a dangling `$ref`. A
`OpenApiSlicer` takes the parsed spec and an operation and emits a minimal,
self-contained sub-spec (shared schemas are duplicated across slices — an accepted
token trade). This component is independent of how slices are *selected* or *rendered*,
so it's testable in isolation and reused by every mode below.

### `digest` — a terse, faithful rendering of the spec (deterministic)

Render each operation as a compact signature plus a shared **type dictionary**, instead
of raw YAML. Roughly:

```
# operations
createAccount(owner:string, currency:string{A-Z}{3})
  POST /accounts -> Account : 201
withdraw(id:long, amount:decimal>=0.01)
  POST /accounts/{id}/withdrawals -> Account : 201 | 422 problem+json
importAccounts(body: csv|text, header X-Default-Currency?: string)
  POST /accounts/import -> Account[] : 201

# types
Account { id:long, owner:string, currency:string, balance:decimal }
TransferSummary { fromId:long, toId:long, amount:decimal }
```

The format is the win; the **mechanism must be a deterministic renderer over the parsed
OpenAPI model, not an LLM**. Most of this is a pure syntactic transform (flatten
operations, resolve `$ref`s, emit `name:type`, show `METHOD /path`, return type), which
a code generator does faithfully and reproducibly.

To stay an *oracle-grade* description (not just request-construction sugar) the renderer
must preserve what a naive compaction drops — these are the acceptance details, drawn
from the demo's own API:

- **Validation constraints** — `currency` is `@Pattern([A-Z]{3}) @Size(3)`, not bare
  `string`; `amount` may carry a min/scale. Render them (`string{A-Z}{3}`,
  `decimal>=0.01`).
- **Param location** — `X-Default-Currency` on `/accounts/import` is a **header**, not a
  body field. Tag location (`header`/`query`/`path`/`body`) so the model places it right.
- **Response shape** — scenarios assert `balance == 500.00`, so `Account`'s fields must be
  defined (the type dictionary), not just named `-> Account`.
- **Status codes** — negative scenarios assert `422` + `application/problem+json`; carry
  the status set and error content type, not just a `throws` name.

Expect a ~3–4× shrink over raw YAML (less than an LLM's lossy ~7×, but **faithful** —
the point for a test oracle). No embedding model, no extra round-trip; it can simply
replace what `OpenApiAdvisor` injects in `full` mode once the spec exceeds a threshold.

### Selecting a subset (for specs too big even as a digest)

**A. `toc` — table of contents + on-demand lookup tool (recommended default for the
big-spec case).**
- Always keep a cheap **index** in the prompt — the **digest signature** line per
  operation is the natural TOC (`createAccount(owner, currency) POST /accounts`). For
  300 operations this is small.
- Add a tool `getOperationSpec(operationId)` (a `@Tool`, like `HttpRequestTool`/
  `SqlTool`) that returns the full self-contained slice (or its digest) on demand — the
  last-mile fidelity (exact schema, constraints, status set) for one endpoint.
- The model browses the index and pulls only what it needs.
- **No embedding model**, pure LLM + tools — fits the local-first design, and the model
  is already tool-calling. Crucially it **can't miss** an operation: it sees every name.

**B. `retrieval` — vector RAG (escalation for very large or poorly-named specs).**
- Lazily slice the spec into per-operation `Document`s → embed into a `VectorStore` →
  per step, retrieve top-k by the **step instruction** → inject those slices.
- Maps onto Spring AI's modular RAG (`RetrievalAugmentationAdvisor` + a
  `DocumentRetriever`), replacing the system-prompt injection for this mode.
- Needs an **embedding model/endpoint** (a new dependency) and risks recall failure
  (below). Prefer hybrid retrieval (semantic + lexical/BM25 over `operationId`/path/
  `tags`) because API names are often literal.

All three compose: the `digest` renders the TOC; `toc` adds the lookup tool; embeddings
only when even the digest TOC is too large.

### Mode selection (config, plugin-owned)

```yaml
inquisitor:
  harness:
    openapi:
      enabled: true
      mode: full            # full | digest | toc | retrieval   (default full)
      max-inline-bytes: 65536   # full→digest auto-switch threshold when mode=auto
      retrieve-top-k: 8         # retrieval mode only
```

`full` is the task-09 `OpenApiAdvisor` (raw YAML). `digest`/`toc`/`retrieval` are
**sibling advisors behind the same seam** — selected by `@ConditionalOnProperty`, each
consuming the same `OpenApiSpecProvider` (the raw fetch is unchanged) plus the new
`OpenApiSlicer`/`OpenApiDigest` renderer. An optional `auto` mode escalates by size:
`full` → `digest` (over `max-inline-bytes`) → `toc`/`retrieval` (when even the digest is
too big).

### Where retrieval keys off the query

For mode B the retrieval query must be the **current step instruction**, not the system
prompt and not an intermediate tool result — inside a step's tool-call loop the advisor
re-runs, so guard against retrieving against a tool output. Mode A sidesteps this
entirely (the model drives lookups).

## Hard parts / risks (call out honestly)

- **Digest fidelity — do NOT compact with an LLM.** Using a model to summarise the spec
  reintroduces the very guessing that task-09 removed, one level up: a dropped constraint
  / enum / required flag / param location makes the model confidently build wrong
  requests, and a model-produced digest is **non-reproducible** run to run — poison for a
  test oracle. The renderer is deterministic; the risk is instead **renderer
  completeness** — it must preserve constraints, param location, response shape, and
  status codes (the acceptance list above), or the digest is lossier than helpful.
- **Recall failure is the headline risk for *retrieval*.** With vector RAG, if top-k
  misses the needed operation the step just fails — the model can't call an endpoint it
  never saw and won't ask for more. Mitigations: generous k (slices are small), hybrid
  semantic + lexical, or prefer `digest`/`toc` where recall is a non-issue. **This is the
  main reason to reach for `digest`/`toc` before `retrieval`.**
- **`$ref` closure correctness** — a slice missing a referenced schema is worse than no
  spec (the model hallucinates the shape). The slicer needs solid transitive-closure +
  cycle handling; this is the bulk of the engineering and where unit tests concentrate.
- **Embedding-model dependency (mode B)** — a second model to run/serve, at odds with
  the local-first, low-config ethos. Mode A avoids it.
- **More inference, fewer tokens (mode A)** — TOC + lookup tool trades context size for
  extra tool round-trips; net win only when the spec is large. Hence size-gating.
- **Added non-determinism** — narrowing what the model sees changes its choices; ties to
  oracle calibration ([task-07](task-07-fault-detection.md)) and the benchmark trace
  ([task-08](task-08-benchmark-task.md), which can assert the right endpoint was hit).

## Out of scope (possible follow-ups)

- An **LLM-generated** digest as a *build-time, human-reviewed, checked-in* artifact
  (temperature 0) — a convenience a person eyeballs once, never a per-run step feeding
  fidelity-critical assertions. The in-scope `digest` mode is deterministic.
- Caching/persisting the embedding index across runs (the spec is static per app
  version).
- Auth-protected `api-docs`; multiple specs/targets (shared with task-09 backlog).

## Relationship to other tasks

Optimises [task-09](task-09-openapi-discovery.md) without touching its seam or the core.
Complementary to task-07/08: digest/retrieval narrow what the model reads, those keep
verdicts honest and can verify the correct operation was selected (and that a leaner
description didn't degrade endpoint choice). Reuses the `OpenApiSpecProvider` and the
advisor-collection seam already in place; `getOperationSpec` joins the existing named
tool registry (`HttpRequestTool`, `SqlTool`).

## Verification (when implemented)

1. `OpenApiSlicer` unit tests (no LLM): a sliced operation is self-contained — every
   `$ref` it names resolves within the slice; cyclic/shared schemas handled; a known
   big fixture spec slices to a small fraction of its size.
2. `OpenApiDigest` renderer unit tests (no LLM): the digest of the demo spec **preserves
   fidelity** — `currency`'s `[A-Z]{3}` constraint, `X-Default-Currency` tagged as a
   header, `Account`'s fields present in the type dictionary, and the `422 problem+json`
   status set on `withdraw`/`transfer`; and it is materially smaller than the raw YAML
   (assert a byte/token ratio). Deterministic: same input → identical output.
3. With `mode=full` (or spec under threshold), behaviour and token footprint are
   identical to task-09 (assert the same injected section).
4. `mode=toc`: the index lists every operation; `getOperationSpec(operationId)` returns
   a valid self-contained slice; an unknown id returns a clear error string (tool
   convention, not a throw).
5. `mode=retrieval`: per-step retrieval surfaces the operation a step needs on a fixture
   spec; measure recall on a labelled step→operation set (the acceptance metric).
6. Against a large synthetic spec, an `intent`/`cucumber` smoke scenario passes with
   `digest`/`toc` while sending materially fewer tokens than `full` (capture the delta).
7. Deleting the plugin still compiles and leaves the core green — removability preserved.

## Docs to update

- `docs/roadmap.md` (task row), `docs/decisions.md` (why size-gated digest+retrieval, why
  a **deterministic** digest rather than LLM compaction, why `digest`/`toc` before
  `retrieval` for an enumerable/named corpus, the slicer's `$ref` closure),
  `README.md` (OpenAPI discovery: the `mode` switch and when to use each),
  `tasks/task-09-openapi-discovery.md` (link the follow-up here), and this file
  (status → implemented).
