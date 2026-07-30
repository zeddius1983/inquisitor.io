# Task 15C — Verbose harness logging

> Status: **📝 planned.** Depends on task 15A for a proven Markdown rendering
> protocol. Task 15B is optional: users may select raw Markdown, manual Logback
> integration, or the automatic starter.

Add an opt-in verbose mode that makes a harness run easy to correlate in the
console. The harness emits portable marker-tagged Markdown and model information;
it does not require Logback or either task-15 logging module.

When enabled, log at INFO:

1. a configured model/connection banner once per harness application context;
2. the scenario heading and description before the scenario's first step;
3. each step heading and instruction immediately before that step runs;
4. the actual model reported by the server once response metadata makes it
   available, explicitly noting a configured/actual mismatch.

## User-facing behavior

Toggle:

```yaml
inquisitor:
  harness:
    verbose: true
```

Default is `false`. This is a deliberate mode, not a request that users enable
framework-wide DEBUG logging.

Indicative configuration banner:

```text
Inquisitor harness — model configuration
  model (configured):  gemma-3-27b-it
  base URL:             http://localhost:1234/v1
  temperature:          0.0
  top-p:                —
  max tokens:           —
  reasoning effort:     —
```

Scenario/step events retain Markdown structure:

```markdown
# Transfer between accounts

Scenario description...

## Move money

Transfer 50 and verify both balances...
```

After the first response that reports a nonblank model:

```text
actual model in use: gemma-3-27b-it-qat (configured: gemma-3-27b-it)
```

Keep looking on later responses until a nonblank model is reported, then log it
only once. A matching actual model is still logged, without the mismatch suffix.

## Rendering choices

Verbose events carry the agreed `INQUISITOR_MARKDOWN` SLF4J marker. The marker is
only a protocol name; core code must not depend on Logback or the optional
renderer artifacts.

Users choose their presentation:

- no renderer dependency: ordinary logs contain readable raw Markdown;
- `inquisitor-logback-markdown`: manual `%mdMsg{marked}` Logback configuration;
- `inquisitor-logback-markdown-starter`: automatic rendering for compatible
  console layouts.

This keeps harness behavior portable to other SLF4J backends and ensures the
pretty-printing choice never controls whether observability data exists.

## Core harness changes

### `InquisitorHarnessProperties`

Add a `boolean verbose` component with `@DefaultValue("false")`. Preserve a
two-argument constructor for `targets`/`datasources` so standalone source users
of the public record are not needlessly broken.

### `HarnessVerboseLogger`

Add a provider-neutral collaborator in the core executor package. It owns:

- `boolean enabled`;
- a pre-rendered optional configuration banner;
- the configured model as a separate optional value (never parse it back out of
  the banner);
- one atomic guard for the banner;
- one atomic guard for the actual-model line.

All methods are no-ops when disabled. Provide
`HarnessVerboseLogger.DISABLED` for compatibility constructors.

Methods:

- `scenarioStarted(Scenario)` — log the banner once, then the `#` scenario name
  and nonblank description as one marker-tagged event;
- `stepStarted(Step)` — log the `##` step title and instruction as one
  marker-tagged event;
- `actualModelResolved(@Nullable String)` — ignore blank values, log exactly
  once, and show the configured value when it differs.

Do not log API keys, credentials, default headers, prompts, tool arguments, or
other potentially sensitive values.

### `ScenarioExecution` / `ScenarioExecutor`

Thread `HarnessVerboseLogger` through `ScenarioExecutor` into
`ScenarioExecution`.

- On the first successful call to `next()`, call `scenarioStarted(scenario)`.
- Immediately before `runner.run(...)`, call `stepStarted(step)`.
- Both the standalone `execute(...)` loop and JUnit's step-at-a-time path already
  funnel through `ScenarioExecution`, so do not duplicate logging in either
  caller.
- Retain `ScenarioExecutor(StepRunner)`, delegating to `DISABLED`.

## Actual response model

Update `LlmStepRunner` to accept the verbose logger while retaining
`LlmStepRunner(ChatClient)` as a compatibility constructor.

Change structured-output extraction from:

```java
.call().entity(StepVerdict.class)
```

to:

```java
.call().responseEntity(StepVerdict.class)
```

Read the nullable entity as before and, when the response/metadata/model is
present and nonblank, pass the model to `actualModelResolved(...)`. Preserve the
existing `JacksonException` to synthetic-FAIL behavior. A conversion failure may
hide that response's metadata; later successful responses should still get a
chance to resolve the model.

## Starter banner assembly

Create the verbose logger bean in `inquisitor-harness-starter` and inject it into
the `LlmStepRunner` and `ScenarioExecutor` beans.

Use `ChatModel.getOptions()`; `getDefaultOptions()` is deprecated for removal in
Spring AI 2.0.

- Generic `ChatOptions`: configured model, temperature, top-p, and max tokens.
- `OpenAiChatOptions`: also reasoning effort and, when relevant, max completion
  tokens.
- Base URL: prefer a nonblank URL present in programmatic OpenAI options, then
  resolve the same property precedence used by Spring AI autoconfiguration:
  `spring.ai.openai.chat.base-url` followed by
  `spring.ai.openai.base-url`.
- Missing values render as `—`.

The base URL is the resolved configured transport value; the provider does not
return it in response metadata. Label configured values honestly and reserve
“actual model” for the server-reported response field.

Create a disabled logger even when no `ChatModel` is present so a user-supplied
`StepRunner` can still obtain a `ScenarioExecutor`. Allow users to replace the
bean with `@ConditionalOnMissingBean`.

The evaluation starter requires no design change: it decorates the actor
`StepRunner`, while actual-model capture stays inside the underlying actor
`LlmStepRunner` and never reports the judge model as the harness model.

## Tests

- `HarnessVerboseLoggerTest`: disabled silence, banner once, scenario/step
  Markdown shape, blank omission, actual model once, match and mismatch.
- `ScenarioExecutorTest`: verify ordering — scenario event, step event, then
  `StepRunner.run(...)` — for whole-scenario and step-at-a-time execution.
- `LlmStepRunnerTest` with a stub in-memory `ChatModel`: structured verdict and
  response metadata are both retained; blank metadata can be followed by a
  later usable model; malformed-response behavior is unchanged.
- Starter context tests: default `verbose=false`, property binding,
  replaceable logger bean, generic options, OpenAI options, and base-URL
  precedence.
- Marker assertion: verbose Markdown events carry `INQUISITOR_MARKDOWN` while
  ordinary existing debug logs do not.
- Existing constructors remain source-compatible and silent.

No live LLM is required for these tests.

## Documentation

- README: document `inquisitor.harness.verbose` beside the model configuration
  and show the three rendering choices above.
- README/module docs: link to the manual and automatic Markdown integration
  artifacts without making either mandatory.
- `docs/roadmap.md`: track tasks 15A, 15B, and 15C independently.
- `CLAUDE.md`: add the two optional logging modules to the module map once they
  exist and record the stable verbose property after 15C lands.

## Non-goals

- No per-tool-call logging; `SimpleLoggerAdvisor` and the tool loggers already
  cover detailed traces at DEBUG.
- No token usage or cost reporting yet, although retaining `ChatResponse`
  metadata creates a future seam.
- No prompt/request-body logging.
- No requirement that consumers use Logback, ANSI, or a Markdown renderer.

## Verification

- `./gradlew :inquisitor-harness:test :inquisitor-harness-starter:test`
- Manual renderer path: verbose events are raw without `%mdMsg`, rendered with
  the documented task-15A configuration.
- Automatic renderer path: adding task 15B's starter renders the same events
  without XML changes.
- `./gradlew check` remains green with no live model.
