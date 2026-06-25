# Task 10 — Partial OpenAPI spec retrieval (context-size optimisation)

Stop injecting the **entire** OpenAPI document into every model call. For large,
production-grade APIs (hundreds of operations, deeply `$ref`-ed schemas) the raw spec
is too big to ride in the system prompt — and because it's re-sent on **every**
round-trip via chat memory, the cost is `spec_size × N_inferences`. Replace
"inject everything" with "surface only the operations relevant to the current step,"
gated by spec size so small specs keep today's behaviour.

This is a **size-gated optimisation of the existing OpenAPI plugin**, not a new
feature surface and not a core change. It was called out as a follow-up in
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

- A **size-gated** retrieval mode in the existing `inquisitor-harness-openapi` plugin;
  **no core change** (the advisor-collection seam from task-09 already suffices).
- Below a configurable size threshold, behaviour is **byte-identical to task-09**
  (`full` mode): send the whole spec.
- Above it, the model is given only the operations it needs for the current step,
  each **self-contained** (its transitively-referenced schemas included).
- Correctness first: a step must still be able to reach **any** endpoint it legitimately
  needs — partial retrieval must not silently hide the right operation (see Risks).

## Design

### A spec "slicer" (the shared, hard part — needed by both approaches)

The retrieval unit is an **operation** (path + method): summary, description,
parameters, request body, responses. The non-trivial work is **`$ref` closure**: an
operation references `#/components/schemas/…`, so a slice must carry the *transitive
closure* of the schemas it touches, or the model sees a dangling `$ref`. A
`OpenApiSlicer` takes the parsed spec and an operation and emits a minimal,
self-contained sub-spec (shared schemas are duplicated across slices — an accepted
token trade). This component is independent of how slices are *selected*, so it's
testable in isolation and reused by both modes below.

### Two selection strategies

**A. `toc` — table of contents + on-demand lookup tool (recommended default for the
big-spec case).**
- Always keep a cheap **index** in the prompt: one line per operation —
  `operationId · METHOD /path · summary`. For 300 operations this is small.
- Add a tool `getOperationSpec(operationId)` (a `@Tool`, like `HttpRequestTool`/
  `SqlTool`) that returns the sliced, self-contained operation on demand.
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

The two compose: index always present, embeddings only when even the index is large.

### Mode selection (config, plugin-owned)

```yaml
inquisitor:
  harness:
    openapi:
      enabled: true
      mode: full            # full | toc | retrieval   (default full)
      max-inline-bytes: 65536   # full→toc auto-switch threshold when mode=auto
      retrieve-top-k: 8         # retrieval mode only
```

`full` is the task-09 `OpenApiAdvisor`. `toc`/`retrieval` are **sibling advisors behind
the same seam** — selected by `@ConditionalOnProperty`, each consuming the same
`OpenApiSpecProvider` (the raw fetch is unchanged) plus the new `OpenApiSlicer`. An
optional `auto` mode picks `full` under `max-inline-bytes`, else `toc`.

### Where retrieval keys off the query

For mode B the retrieval query must be the **current step instruction**, not the system
prompt and not an intermediate tool result — inside a step's tool-call loop the advisor
re-runs, so guard against retrieving against a tool output. Mode A sidesteps this
entirely (the model drives lookups).

## Hard parts / risks (call out honestly)

- **Recall failure is the headline risk.** With vector RAG, if top-k misses the needed
  operation the step just fails — the model can't call an endpoint it never saw and
  won't ask for more. Mitigations: generous k (slices are small), hybrid semantic +
  lexical, or prefer mode A (TOC) where recall is a non-issue. **This is the main reason
  to start with `toc`.**
- **`$ref` closure correctness** — a slice missing a referenced schema is worse than no
  spec (the model hallucinates the shape). The slicer needs solid transitive-closure +
  cycle handling; this is the bulk of the engineering and where unit tests concentrate.
- **Embedding-model dependency (mode B)** — a second model to run/serve, at odds with
  the local-first, low-config ethos. Mode A avoids it.
- **More inference, fewer tokens (mode A)** — TOC + lookup tool trades context size for
  extra tool round-trips; net win only when the spec is large. Hence size-gating.
- **Added non-determinism** — narrowing what the model sees changes its choices; ties to
  oracle calibration ([task-07](task-07-negative-scenarios.md)) and the benchmark trace
  ([task-08](task-08-benchmark-task.md), which can assert the right endpoint was hit).

## Out of scope (possible follow-ups)

- A `$ref`-**compressing** digest of the *full* spec (orthogonal: shrink everything vs.
  select a subset); could feed `full` mode for medium specs.
- Caching/persisting the embedding index across runs (the spec is static per app
  version).
- Auth-protected `api-docs`; multiple specs/targets (shared with task-09 backlog).

## Relationship to other tasks

Optimises [task-09](task-09-openapi-discovery.md) without touching its seam or the core.
Complementary to task-07/08: retrieval narrows the model's view, those keep verdicts
honest and can verify the correct operation was selected. Reuses the `OpenApiSpecProvider`
and the advisor-collection seam already in place; `getOperationSpec` joins the existing
named tool registry (`HttpRequestTool`, `SqlTool`).

## Verification (when implemented)

1. `OpenApiSlicer` unit tests (no LLM): a sliced operation is self-contained — every
   `$ref` it names resolves within the slice; cyclic/shared schemas handled; a known
   big fixture spec slices to a small fraction of its size.
2. With `mode=full` (or spec under threshold), behaviour and token footprint are
   identical to task-09 (assert the same injected section).
3. `mode=toc`: the index lists every operation; `getOperationSpec(operationId)` returns
   a valid self-contained slice; an unknown id returns a clear error string (tool
   convention, not a throw).
4. `mode=retrieval`: per-step retrieval surfaces the operation a step needs on a fixture
   spec; measure recall on a labelled step→operation set (the acceptance metric).
5. Against a large synthetic spec, an `intent`/`cucumber` smoke scenario passes with
   `toc` while sending materially fewer tokens than `full` (capture the delta).
6. Deleting the plugin still compiles and leaves the core green — removability preserved.

## Docs to update

- `docs/roadmap.md` (task row), `docs/decisions.md` (why size-gated retrieval, why
  `toc`-before-`retrieval` for an enumerable/named corpus, the slicer's `$ref` closure),
  `README.md` (OpenAPI discovery: the `mode` switch and when to use it),
  `tasks/task-09-openapi-discovery.md` (link the follow-up here), and this file
  (status → implemented).
