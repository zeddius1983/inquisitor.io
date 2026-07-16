# Notes: LLM-authored mock server

Working design notes for `inquisitor-mock`. The implementation outline remains
in [`tasks/task-06-mock-server.md`](../tasks/task-06-mock-server.md); this note
captures the reasoning, trade-offs, and refinements behind it.

## Problem

The app under test (AUT) often depends on third-party HTTP services: FX rates,
account status, asset search, fraud scoring, and similar APIs. Traditional stub
servers handle individual cases well, but a realistic dependency accumulates a
large amount of endpoint-specific matching and response code. Rich search APIs
make that cost especially visible.

The proposed differentiator is to author dependency behavior in natural
language, optionally paired with a Java response type, and let an LLM
materialize the response:

```java
mock(get("/accounts/{id}/state"), """
    Return FROZEN for ids 5, 8, and 12,
    SUSPENDED for ids 100 through 400,
    and ACTIVE otherwise.
    """, AccountStatus.class);
```

This is closer to an **LLM-backed service simulator** than a conventional fixed
mock. That distinction should be explicit: live responses improve authoring and
exploration, but they do not inherit the determinism normally expected from a
test fixture.

## Central design tension

Putting the LLM in the request path enables open-ended behavior without
reimplementing each upstream service. It also makes the dependency stochastic,
slower, and potentially flaky.

A compile-once design does not solve this universally. A decision table works
for account states, while a realistic asset search would need a corpus, filter
semantics, sorting, pagination, totals, and eventually mutation. Generalizing
that approach far enough risks building a query language and a collection of
mini service engines.

The current direction is therefore:

- Live LLM generation is the primary capability and the MVP.
- Memoization stabilizes responses after their first materialization.
- File persistence can promote generated responses into reusable fixtures.
- Replay-only execution provides deterministic tests and CI without an LLM.
- Fixed-corpus selection is an optional response strategy, not a replacement
  for live generation.
- A fully compiled query-plan interpreter is deferred until real use proves it
  necessary.

Do not base correctness on the expectation that future models will become
perfectly deterministic. Better models will improve the experience, but the
design still needs validation, observability, memoization, and replay.

## Keep response strategy and lifecycle separate

Two independent choices were initially discussed as one set of "modes." They
should remain separate in the design.

### Response strategy: how a response is produced

- **Generative**: the LLM creates response values from the rule and request.
- **Corpus selection**: the LLM selects records from a fixed, vetted dataset.
- **Static/deterministic lookup**: a possible later optimization for trivial
  exact matches.

### Lifecycle policy: when the model may be invoked

The lifecycle can be expressed through three orthogonal policies:

| Policy | Options |
|---|---|
| Storage | memory, file |
| On cache miss | generate, fail |
| Writes | enabled, disabled |

Useful user-facing presets follow from those policies:

| Behavior | Storage | On miss | Writes | Use |
|---|---|---|---|---|
| Live + session memo | memory | generate | enabled | Local exploration |
| Live + persistent memo | file | generate | enabled | Reusable local responses |
| Replay only | file | fail | disabled | Deterministic tests and CI |

`RECORD` is not mechanically distinct from file-persisted memoization. Both
mean "read on hit; generate and persist on miss." The word *recording* is useful
for the workflow in which a developer reviews a persistent entry and promotes
it to a version-controlled cassette, but it need not be a separate serving
mode.

The meaningful boundary is replay-only: it forbids model calls and fails on an
unknown request.

One possible fluent expression of the live + session-memo preset is:

```java
mock(get("/accounts/{id}/state"))
    .respond(AccountStatus.class, """
        Return FROZEN for ids 5, 8 and 12,
        SUSPENDED for ids 100 through 400,
        and ACTIVE otherwise.
        """)
    .mode(GENERATE_ON_MISS)
    .cache(SESSION);
```

Here `GENERATE_ON_MISS` permits an LLM call only when no materialized response
exists, while `SESSION` keeps that response stable for the lifetime of the mock
server. The names illustrate the policy split; they are not yet a committed
public API.

## MVP request pipeline

For a matched HTTP endpoint:

1. Bind path variables, query parameters, selected headers, and body into a
   normalized request model.
2. Build the canonical memo key and look it up.
3. On a hit, return the materialized response without invoking the LLM.
4. On a miss with generation disabled, fail with a precise unmatched-request
   diagnostic.
5. On a miss with generation enabled, prompt the independently configured mock
   model with the rule, request data, and response schema.
6. Deserialize and validate the response. Allow a small, bounded repair attempt
   when the output is invalid.
7. Persist the validated HTTP response envelope when file storage is enabled.
8. Record the interaction for inspection, assertions, and the evaluation trace.

The materialized HTTP value should be an envelope rather than only a body:

```text
status + headers + typed body
```

This leaves room for redirects, validation errors, rate limiting, empty
responses, and other realistic upstream behavior.

### Structured output is not a complete guarantee

Mapping a response with Spring AI's `.entity(Type.class)` may parse or convert
model text; it does not necessarily constrain model decoding. Where the model
provider supports JSON-schema response formatting, use it. In all cases,
deserialize and validate locally.

Schema validation proves structure and allowed value domains. It does not prove
that a structurally valid response followed the natural-language rule.

## Memo keys and persistence

A canonical HTTP request key will likely include:

- mock endpoint identity;
- method and normalized path;
- sorted query parameter multimap;
- only headers declared relevant to matching;
- canonicalized JSON body, where applicable;
- a fingerprint of the rule and response schema.

It should ignore incidental data such as the mock server's random port, header
ordering, and JSON property ordering. Sensitive credentials should not be
persisted merely to distinguish requests; relevant authentication states need a
safe normalized representation.

The rule/schema fingerprint prevents a stale response from silently surviving a
contract change. Replay-only should report a stale entry clearly rather than
using it or regenerating it.

Persistent entries have two possible roles:

- **Disposable cache**: local optimization, safe to clear and normally ignored
  by version control.
- **Cassette**: reviewed fixture, stable until explicitly refreshed and suitable
  for version control.

Existing cassette entries should not be silently overwritten. Refresh is an
explicit operation so rerunning a test cannot alter its environment unnoticed.

Replay-only is intentionally incomplete: it replays requests that have been
observed and approved; it does not implement the upstream service for arbitrary
inputs. An unknown request should produce a clear mock-server failure and be
visible in the interaction trace.

## Fixed-corpus selection

A later hybrid strategy can constrain the model to a human-vetted collection:

```json
[
  { "id": "fx-usd-eur", "ccy1": "USD", "ccy2": "EUR", "rate": 0.8 },
  { "id": "fx-usd-rub", "ccy1": "USD", "ccy2": "RUB", "rate": 77.0 }
]
```

The corpus can be generated offline by an LLM, reviewed once, and committed.
At runtime the model chooses from approved data instead of inventing objects.

This removes:

- fabricated domain values;
- objects outside the approved test universe;
- mutation of vetted record contents;
- much of the response-shape and data-coherence risk.

It does not prove that the correct records were selected. Multi-predicate
filtering, boundary conditions, and semantic query interpretation remain model
decisions.

### Return identifiers, not copied objects

For corpus selection, require the LLM to return only record identifiers:

```json
{ "matchingIds": ["asset-123", "asset-781"] }
```

The responder then rejects unknown or duplicate identifiers and materializes
the original records verbatim. This prevents the model from subtly editing an
approved object while copying it into the response.

Exact lookup endpoints may eventually bypass the LLM with a small generic
named-field matcher. That is an optimization, not an MVP requirement.

### Collection endpoints

For collection/search responses, deterministic post-processing can remove the
model from mechanical work:

1. The LLM selects the complete matching identifier set.
2. The responder caches that membership set.
3. Code applies the configured sort.
4. Code calculates counts and pages.
5. Code slices the requested page and constructs the envelope.

The membership cache key must exclude presentation parameters such as `page`,
`size`, and normally `sort`. Otherwise each page causes an independent LLM
selection and pages may disagree about membership or totals.

Pagination should start with an intentionally narrow contract. Generic sorting
quickly grows beyond a few lines once nulls, nested fields, multiple clauses,
types, and collation are supported. Do not claim universal upstream semantics.

Large corpora will eventually hit model context and latency limits. Bound corpus
size for the first version; retrieval, chunking, or a deterministic query engine
can be considered after measuring actual demand.

## Architecture direction

The reusable core concepts are:

- a normalized inbound interaction;
- an authored rule and optional response schema;
- a response strategy;
- memo/cassette storage;
- validation and response materialization;
- an interaction recorder.

HTTP is the first transport. JMS and Kafka can reuse generation, validation,
storage, and recording concepts, but they are not merely HTTP-shaped bindings.
Ordering, acknowledgements, redelivery, partitions, offsets, consumer groups,
and asynchronous timing require transport-specific runtimes. Preserve the
shared seams without promising that a generic `Channel` makes those transports
free.

The mock model must be independently configurable from the harness actor and
evaluation judge. Using the same configured role to drive the AUT and author its
environment creates unnecessary circularity.

## Spring and harness integration

For local and test startup, the HTTP mock can bind a random port and publish each
named upstream's base URL before the AUT's `@ConfigurationProperties` are bound,
following the zero-config spirit of the demo database starter.

Every inbound call should be recorded independently of how its response was
produced. The spy can support:

- direct assertions that the AUT called a dependency correctly;
- diagnostics for unexpected or unmatched calls;
- evaluation traces that let the judge verify outbound side effects which are
  invisible through the AUT's public response.

## Illustrative configuration

Local generation with in-memory memoization:

```yaml
inquisitor:
  mock:
    memo:
      storage: memory
    on-miss: generate
```

Persistent local memoization:

```yaml
inquisitor:
  mock:
    memo:
      storage: file
      directory: build/inquisitor/mock-cache
    on-miss: generate
```

Reviewed cassette in replay-only CI:

```yaml
inquisitor:
  mock:
    memo:
      storage: file
      directory: src/test/resources/mock-cassettes
    on-miss: fail
```

These property names are illustrative, not a committed public API.

## Deferred questions

- Whether memo storage is scoped per test, scenario, endpoint, or mock server.
- Cassette format, concurrency, atomic updates, and explicit refresh tooling.
- Which validation API expresses semantic invariants beyond the Java type.
- Whether response rules control status and headers directly or through a typed
  envelope DSL.
- How much conversational/world state live generation should retain across
  related but non-identical requests.
- How a bounded collection pagination contract is declared.
- How corpus files are authored and versioned.
- Whether replay misses return a dedicated HTTP error, abort the test through a
  side channel, or both.
- How mock response quality and variance are measured with the existing
  evaluation and benchmark facilities.

## Current recommendation

Build the smallest path that proves the differentiator:

1. HTTP endpoint declaration with a natural-language rule and optional type.
2. Live generation with provider-supported structured output plus local
   deserialization and validation.
3. Canonical-request memoization, initially in memory and then in a file.
4. Interaction recording and diagnostics.
5. Replay-only over reviewed persistent entries.

Add corpus selection only after the live path is measured. Add a deterministic
query-plan engine only if real tests require cross-request semantics that
memoization and bounded deterministic post-processing cannot provide.
