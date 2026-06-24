# Task 09 — OpenAPI/Swagger discovery (optional, plugin module)

Give the LLM the application's full REST API description (OpenAPI/Swagger) so it can
choose endpoints, methods, paths, and request/response shapes itself — the
prerequisite for the `intent` scenario style (pure business intent, no endpoints in
the scenario text). The spec is **live-fetched from the app under test** by default,
with a static-location fallback, and injected **raw (YAML)** into the system prompt
via a **Spring AI `Advisor`**.

This is explicitly a **nice-to-have plugin, not a core feature.** It must:

- not entangle the core executor/evaluator logic,
- be enable/disable-able via configuration,
- be **removable painlessly** — deleting the module leaves the core untouched and
  still useful.

> Status: **planned — awaiting review.** No code yet. Open design decisions are
> marked **[DECIDE]**.

## Why

Today scenarios spell out endpoints and payloads (see `scenarios/positive/explicit`)
because the model has no API description — guessing paths is unreliable (see
[[project_openapi_discovery]]). Handing it the spec turns "guess the API" into "read
the API," which makes an `intent` bucket a fair test instead of a coin flip, and
retires the standing caveat that we keep explicit request bodies until discovery
lands.

It is downstream-enabling, not load-bearing: the `explicit` and `cucumber` buckets
must keep working with this feature **absent or disabled**.

## Goals

- A new optional module **`inquisitor-harness-openapi`** (+ **`-openapi-starter`**),
  added by a consumer as a test dependency. Core has **zero** compile-time reference
  to it.
- Default **live fetch** of the spec from the app target (`GET
  {appBaseUrl}/v3/api-docs.yaml`), resolved **lazily** the first time it's needed.
- **Fallback / override** to a static location (`classpath:`/`file:`/`http:`).
- Inject the spec **raw, as YAML**, into the system prompt through an
  **`OpenApiAdvisor`** (a Spring AI `Advisor`).
- The only core change: the ChatClient autoconfig **collects available `Advisor`
  beans** and registers them — a generic, advisor-agnostic seam.
- Off-by-default-safe: with the module absent **or** `enabled=false`, behaviour is
  byte-identical to today.

## Design

### Module layout & dependency direction

```
inquisitor-harness-openapi          # OpenApiAdvisor, ApiSpecProvider(s), properties, autoconfig
inquisitor-harness-openapi-starter  # autoconfiguration registration
```
Direction: `inquisitor-harness-openapi` → `inquisitor-harness` (for
`HttpTargetRegistry`) → Spring AI. **The core never depends on the plugin** (DIP).
Consumer adds `inquisitor-harness-openapi-starter` (test scope) to switch it on;
drop the dependency to remove it.

### The seam: collect `Advisor` beans in the core ChatClient (the only core touch)

`InquisitorHarnessAutoConfiguration#inquisitorChatClient` today hard-codes its
advisors:

```java
.defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),
        new SimpleLoggerAdvisor())
```

Change: also collect any `Advisor` beans in the context (mirroring how it already
collects `ToolCallback`/`ToolCallbackProvider`) and append them:

```java
ObjectProvider<Advisor> advisors        // new parameter
...
val all = new ArrayList<Advisor>(List.of(
        MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor()));
advisors.orderedStream().forEach(all::add);   // ordered by Advisor's getOrder()
... .defaultAdvisors(all.toArray(Advisor[]::new))
```

This is generic (not OpenAPI-aware) and behaviour-preserving: **no advisor beans →
identical to today.** `HarnessSystemPrompt.TEXT` stays the static base prompt; no
composer/SPI is introduced — the advisor chain is the extension point. **OCP** (add
behaviour without editing core logic), **DIP** (core depends on Spring AI's `Advisor`
abstraction, the plugin implements it).

### `OpenApiAdvisor` (in the plugin) — verified against Spring AI 2.0

Implement **`BaseAdvisor`** and augment the system message in `before(...)` — the
same shape `QuestionAnswerAdvisor` uses to inject RAG context (confirmed in the
Spring AI 2.0 reference):

```java
class OpenApiAdvisor implements BaseAdvisor {
    private final ApiSpecProvider specProvider;

    @Override
    public ChatClientRequest before(ChatClientRequest req, AdvisorChain chain) {
        val yaml = specProvider.spec();             // throws if enabled but unobtainable
        return req.mutate()
                .prompt(req.prompt().augmentSystemMessage(section(yaml)))
                .build();
    }
    @Override public ChatClientResponse after(ChatClientResponse r, AdvisorChain c) { return r; }
    @Override public int getOrder() { /* see ordering note */ }
    @Override public String getName() { return "openApiAdvisor"; }
}
```

Confirmed APIs (Spring AI 2.0): `BaseAdvisor.before(ChatClientRequest, AdvisorChain)`
/ `after(ChatClientResponse, AdvisorChain)`; `Prompt.augmentSystemMessage(String)`
(creates a system message if none exists); `Advisor extends Ordered` (`getOrder()`,
`getName()`); ChatClient builder `.defaultAdvisors(Advisor...)`.

Notes:
- The advisor runs **per request**, so the spec is present on every round-trip — which
  is what we want — at the cost of one cached-string augmentation each call.
- **Ordering [DECIDE]:** pick an order relative to the chat-memory advisor
  (`DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER`) and the logger (which should see the
  augmented request). Touching the *system* message shouldn't conflict with memory's
  conversation messages, so order is low-risk — settle it in code.
- **Autoconfig ordering:** the plugin's advisor bean must be resolvable when the core
  builds the ChatClient; `ObjectProvider` is lazy, and the openapi autoconfig declares
  `@AutoConfiguration(before = InquisitorHarnessAutoConfiguration)` to be safe.

### `ApiSpecProvider` strategy (lazy live-fetch first)

```java
public interface ApiSpecProvider { String spec(); }   // YAML text; throws if unobtainable
```
- `HttpApiSpecProvider` — reads the app base URL from the existing
  `HttpTargetRegistry` (already populated post-startup by the JUnit layer), fetches
  `{base}{path}` **lazily on first call, then caches** for the run. **No JUnit-layer
  hook required** — the linchpin that keeps the plugin out of the core executor *and*
  the JUnit module.
- `ResourceApiSpecProvider` — loads a static `classpath:`/`file:`/`http:` location.

Resolution (all gated by `enabled`): explicit `location` → `ResourceApiSpecProvider`;
else live fetch → `HttpApiSpecProvider`. **SRP/ISP/LSP**: obtaining the spec
(`ApiSpecProvider`) and injecting it (`OpenApiAdvisor`) are separate, one-method
responsibilities; the two providers are interchangeable.

**Failure handling (decided — fail fast):** because `enabled=true` is an explicit
opt-in to OpenAPI injection, a fetch/parse failure must **fail loudly**, not degrade
silently — silent "no spec" would mislead the user into thinking the spec reached the
model when it didn't. So when enabled and the spec cannot be obtained,
`ApiSpecProvider.spec()` throws a clear exception (at first use) that surfaces on the
scenario run, naming the location/URL it tried and why it failed. (No silent
pass-through, and no separate "strict" toggle — fail-fast *is* the enabled
behaviour.)

### Format & delivery

- **YAML** (fetch `/v3/api-docs.yaml`; accept `.yaml/.yml/.json` for static and
  normalise to YAML). The spec rides in the system prompt and is re-sent on **every**
  round-trip (chat memory), so YAML's ~15–30 % token saving compounds — important for
  32k-ctx local models ([[project_harness_local_model]]).
- **Raw** injection to start (no digest). Note for later: raw specs hoist bodies into
  `components/schemas` behind `$ref`s, and re-send cost grows with size — a
  **`$ref`-resolving digest gated by a size threshold** is the documented follow-up,
  not v1.

### Configuration & ergonomics

Plugin-owned `@ConfigurationProperties` (kept out of core `InquisitorHarnessProperties`
so removal is clean):

```yaml
inquisitor:
  harness:
    openapi:
      enabled: true            # off-switch (@ConditionalOnProperty)
      location:                # optional static override (classpath:/file:/http:)
      path: /v3/api-docs.yaml  # appended to the target base URL for live fetch
      target: app              # which registered HttpTarget to fetch from
```

- **`@Harness` is NOT changed** — OpenAPI is a separate module and must not leak into
  the core annotation.
- **v1 is property-only.** The `inquisitor.harness.openapi.*` properties above are the
  sole configuration surface; they cover both the JUnit suites and the standalone
  `ScenarioTests` path (which has no annotation). A plugin-owned `@ApiSpec` ergonomic
  override is a **follow-up**, not v1 (see Out of scope).

### Demo wiring & the springdoc-on-Boot-4 question (do this first)

**Step 1 is a spike:** does a springdoc-openapi version resolve on **Spring Boot
4.0.6 / Framework 7** (Jackson 3, jakarta) and emit `/v3/api-docs.yaml`? springdoc has
historically tracked Boot 3 / Framework 6, so this is the main unknown.
- **If yes:** add it to the demo (`testImplementation` suffices — discovery is a
  test-time concern), enable the plugin, live fetch just works.
- **If not:** ship a hand-written static `openapi.yaml` fixture for the demo (2
  controllers — trivial, deterministic) and point the plugin at it via `location`. The
  **plugin is spec-source-agnostic**, so the demo's choice never blocks the feature.

## Hard parts / risks (call out honestly)

- **springdoc + Boot 4 maturity** — the spike de-risks; static fixture is the escape
  hatch.
- **Token / context cost** — raw spec re-sent every turn; fine for the demo, scales
  badly → size-gated digest follow-up.
- **Added non-determinism** — letting the model pick endpoints widens variance; ties
  directly to oracle calibration ([task-07](task-07-negative-scenarios.md)) and the
  benchmark trace ([task-08](task-08-benchmark-task.md) can assert the *right* endpoint
  was hit).
- **Spec leaves the building** — the spec is sent to the (possibly remote) LLM; worth
  a README note for consumers with sensitive/large APIs.
- **Advisor ordering** — get the precedence right so memory + logging still behave;
  low-risk because we touch the system message, not conversation messages.

## Out of scope (possible follow-ups)

- The `intent` scenario bucket itself (the downstream consumer/acceptance test —
  separate task; this delivers the mechanism + a minimal smoke proof).
- `$ref`-resolving **digest / compression** (size-gated).
- A **per-endpoint retrieval tool** (`describeEndpoint`) for very large APIs — the
  scale path beyond context injection.
- A plugin-owned **`@ApiSpec("classpath:openapi.yaml")`** annotation as an ergonomic
  alternative to the property (needs a small JUnit extension in the openapi module).
- Auth-protected `api-docs` endpoints; multiple specs / targets.

## Relationship to other tasks

Enables the `intent` bucket. The advisor-collection seam may also serve future
advisors. Independent of task-07/08 but complementary: discovery adds freedom, those
two keep the verdicts honest.

## Verification (when implemented)

1. `./gradlew build` unaffected; with the module absent **or** `enabled=false`, the
   ChatClient gets no extra advisor and the `explicit`/`cucumber` buckets behave
   exactly as before (unit-assert the advisor list).
2. Unit tests (no LLM): `HttpApiSpecProvider` fetches + caches lazily and **throws a
   clear error when the spec is unreachable**; `ResourceApiSpecProvider` loads a
   classpath spec; `OpenApiAdvisor.before(...)` augments the system message with the
   spec section.
3. With the module enabled and the demo serving a spec, an `intent`-style smoke
   scenario (no paths/bodies in the text) passes on a strong config — i.e. the model
   used the spec to reach the right endpoints.
4. Deleting the `inquisitor-harness-openapi` module compiles and leaves the core green
   — "removable painlessly" demonstrated.

## Docs to update

- `docs/roadmap.md` (new task row), `docs/decisions.md` (why an advisor-based plugin +
  the advisor-collection seam, YAML, raw-then-digest), `README.md` (optional OpenAPI
  discovery: module, config, live-fetch default, spec-leaves-to-LLM note; retire the
  explicit-bodies caveat), `CLAUDE.md` (module map — two new modules), this file
  (status → implemented), and [[project_openapi_discovery]] (premise acted on).
