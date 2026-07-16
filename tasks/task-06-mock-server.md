# Task 06 — `inquisitor-mock`: LLM-authored dependency mocks

Stub the **third-party dependencies the app under test (AUT) calls outward** —
an FX-rate feed, an account-state service, an asset-search API — for the duration
of a scenario (and for local dev). The distinguishing idea: mocks are **authored
in natural language / typed rules and materialised by an LLM**, not hand-coded
stub matchers. HTTP first; JMS, Kafka and other transports later behind the same
core.

> Status: **🧭 design outline** — captured from a 2026-07-16 design discussion.
> Not scheduled; the roadmap gate ("decide the mock-server design first") is what
> this document exists to close. MVP shape is settled (**live + memo**); the
> deterministic mode is sketched, not committed.

## Why not WireMock (the motivation)

WireMock/MockServer handle a simple endpoint fine, but the per-case stubbing
**doesn't scale**: every new account-state, every new scenario, is another
`stubFor(...)` with path matchers, priorities and response templates — a lot of
hand-written, bug-prone code that must change for each new case. A rich search
endpoint (`/assets?types=…&minPrice=…&page=…`) multiplies this. The value of
`inquisitor-mock` is **not** a stub engine — it's authoring mocks the same way
scenarios are authored (natural language an LLM interprets), plus integration
with the Spring wiring and the evaluation trace. If it were only an embedded stub
server, consumers should use WireMock and this module shouldn't exist.

## The core idea

Declare a mock by endpoint signature + a rule, e.g.:

```java
mock(get("http://host:8080/accounts/{id}/state"), """
    Return { "id": {id}, "state": {state} }
    where {state} is FROZEN for ids 5, 8, 12; SUSPENDED for 88, 15, 99; ACTIVE otherwise
    """, AccountStatus.class);   // typed → structured output, schema-checked
```

The LLM turns the rule into responses. **Determinism is the whole design
tension** — a mock is meant to be the *trustworthy* part of a test, so an LLM in
the request path makes the fixture itself stochastic (a flaky mock → false red
tests → the suite loses trust). The resolution is a quality ladder selected by
use case, all from one authored rule.

## Endpoint shapes (drives which mode fits)

| Shape | Example | Natural materialisation |
|---|---|---|
| **Lookup / scalar** | account state by id, FX rate by pair | LLM selects from a small fixed set; trivial |
| **Collection / search** | `/assets?…` (filters + pagination) | LLM selects the matching subset from a corpus; code paginates |
| **Generative** | "fraud score from free-text description" | genuinely live per-request LLM |

## Serving modes — MVP is **live + memo**

Determinism requirement depends on the use case, so don't force one mode:

- **Local dev / exploration** (the motivating case — "run my service and poke at
  asset search, get something meaningful back"): determinism barely matters. Live
  is the right, simpler call — infinite plausible data, no fixture maintenance,
  **no engine to build.**
- **CI assertions** (a scenario asserts "search apple → AAPL"): a flaky mock
  corrupts the signal; determinism matters. Needs a freeze/compiled path.

**MVP = live + structured output + persistent memoization:**

1. Embedded server per endpoint template → bind path/query/body to a variable map.
2. Prompt the **mock model** (configured independently of actor and judge — a
   self-authored environment is circular) with the rule + item schema + bound
   request → **structured output** (`.entity(Type.class)` / JSON-schema
   constrained). Shape is then guaranteed; only *values* can drift.
3. **Memoize by (endpoint, normalized request).** First hit → LLM; thereafter
   served from cache. Gives intra-run stability (paging, retries, repeats) for
   free — and **persisting the cache across runs is the organic snapshot** (the
   seed of determinism with almost no new code).

## The corpus-selection hybrid (the determinism story, deferred)

Rather than an LLM generating values live (value hallucination — the scariest
flakiness) **or** a hand-written filtering engine (a lot of bug-prone code):

- **A fixed, human-vetted corpus in a file** (e.g. `fx-rates.json`,
  `assets.json`) — well-formed, coherent data. The corpus may be **LLM-generated
  offline, then reviewed and committed** (LLM does content generation, its
  strength, offline; a human vets once; hallucination is gone permanently).
- **The LLM does *selection*** — "which items from this corpus match this
  request" — returning the matching subset. This keeps the author's idea intact:
  *no filter engine, no query-plan DSL to write.*

This removes the worst flakiness (invented values/shape) and is **solid as-is for
lookup / exact-match** endpoints (FX pair, state by id — selection is trivial).

**Caveat for collection/search:** selecting from a corpus keeps the LLM in the
two seats it is *worst* at — multi-predicate boolean filtering and, especially,
**pagination + counts + sort arithmetic** (which also needs cross-request
coherence: `page 0` and `page 1` must agree on `totalElements`). So for
collection endpoints, split by tool strength:

- **LLM selects** the matching set from the corpus (no filter engine — the
  author's idea).
- **~30 lines of generic, endpoint-agnostic code** does sort + paginate + count +
  envelope. Tiny, not the "lot of code" a full query engine would be, and it
  makes paging coherent by construction — lifting the residual flakiness out of
  the LLM's hands where it's weakest and code is perfect.

The full "corpus + query-plan interpreter" (LLM infers a declarative
param→predicate plan, a fixed engine executes it — fully deterministic *and*
coherent) stays a **later "freeze for CI" mode**, added only when an assertion
scenario needs cross-request consistency that memo can't give.

## Architecture — transport-agnostic from day one

HTTP is the first binding, not the design centre, so JMS/Kafka slot in without a
rewrite:

- **`Channel`** — inbound binding (HTTP endpoint template now; JMS queue / Kafka
  topic later).
- **`Rule`** — the NL text or typed spec + its materialisation strategy
  (live / memo / corpus-select).
- **`Responder`** — resolves a bound request to a response; transport-independent.

The only transport-specific part is **request binding** — extracting the named
variables the rule references (`{id}` from a path template; message
key/headers/payload fields for Kafka). Get that seam right and new transports are
new bindings, not new engines. Name the served upstreams (symmetric with the
driving side's `HttpTargetRegistry`).

## Wiring (consistent with the repo)

Same mechanism `inquisitor-demo-db-starter` uses for Postgres: the mock starts on
a random port and its autoconfiguration publishes each upstream's base-URL into
the Spring `Environment` **before** the AUT's `@ConfigurationProperties` resolve,
so e.g. `fx.api.url` points at the mock with zero manual config.

## Spy → evaluation trace (the cross-module payoff)

The `Responder` records every inbound call. That unlocks assertions the actor
**cannot make through the front door** — e.g. "a transfer over 10k must be
reported to the compliance API." Recorded upstream calls can be:

- asserted by the actor via a tool (`mockReceivedRequests`), and/or
- fed into the **evaluation trace**, so the judge verifies what the AUT *did to
  its dependencies*, not just what it returned. This is the mock × evaluation
  product — the strongest reason the module earns its place.

## Decisions

- **Live + memo is the MVP**; corpus-select and the full freeze/engine are
  additive later modes, not competing architectures — one authored rule,
  materialised differently per use case.
- **Mock model is independent** of actor and judge (avoid circular self-authoring).
- **Typed rules use structured output** so response shape is guaranteed.
- **Code-declared `mock(...)` fluent API for v1**, not scenario-markdown grammar
  (more deterministic, dodges the flexmark-parser cost); move rules into scenarios
  later only if it earns it.
- Not published to Maven Central yet (`docs/decisions.md`: mock modules stay
  unpublished/experimental).

## Deferred / out of scope

- **Stateful / sequenced mocks** (503-then-200 for retry testing; "FROZEN until a
  transfer, then ACTIVE") — expressible in NL but needs the responder to carry
  state; don't let the `Responder` abstraction preclude it. v2.
- **Mutable corpus** (POST an order → later GET reflects it) — the query-over-
  dataset model extends to it (writes mutate the in-memory dataset). v2.
- **JMS / Kafka / other transports** — the reason the core is transport-agnostic,
  but HTTP ships first.
- **Full query-plan interpreter** (the deterministic "freeze" engine) — only when
  a CI assertion needs cross-request coherence.

## Open questions

- Demo dependency to make this concrete: add an FX-rate + account-state (and
  later asset-search) upstream to `inquisitor-demo` so the mock has a real
  consumer, mirroring how the demo grounds every other module.
- Corpus authoring ergonomics: hand-written vs. the offline-LLM-generate-then-
  review flow — which to build first.
- How memo persistence is keyed/stored (per-scenario file? shared snapshot dir?)
  and how "freeze" is triggered.

## Verification (when built)

- `./gradlew build` green with the new modules.
- `:inquisitor-demo:bootRun` locally → the AUT's FX/state/asset-search calls hit
  the mock and get meaningful, well-formed responses with varied
  pages/types/empties, no real upstream.
- A scenario asserting an outbound side effect (compliance call) passes via the
  spy/trace path.
