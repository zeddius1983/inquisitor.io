# Task 15C — Semantic harness logging

> Status: **✅ done.** Depends on task 15A for the Markdown marker/rendering
> protocol. Task 15B remains optional for automatic Spring Boot console rendering.

Make scenario runs easy to correlate without coupling execution code to one log
format or one logging backend. The harness exposes semantic actor, judge, and
scenario logging seams with plain-text and marker-tagged Markdown implementations.

## User-facing behavior

Select the format:

```yaml
inquisitor:
  harness:
    logging:
      format: markdown # plain | markdown; default plain
```

The loggers report:

1. the scenario heading and description before its first step;
2. each actor step breadcrumb, heading, and instruction in one event immediately
   before execution;
3. actor and judge invocation lifecycle diagnostics;
4. normal scenario completion or infrastructure abort.

Markdown messages have two leading newlines and one trailing newline, producing
an empty line after the conventional Logback prefix and separation before the
next event. They carry the shared `INQUISITOR_MARKDOWN` SLF4J marker. Plain
messages use ordinary unmarked log lines. Scenario narration is INFO,
actor/judge invocation details are DEBUG, and aborts are WARN. Markdown actor
starts use a Powerline breadcrumb
(`optional actual actor model`, `scenario`, `step`, `progress`, `status`); the optional renderer turns it into
Powerlevel10k-style pills while raw/plain output remains readable. Progress uses
a five-cell rounded `▰`/`▱` gauge followed by the current/total step counter.
Actor completion reuses the breadcrumb with the verdict as its status, followed
by a dedicated `⧖` segment containing a human-scale duration in milliseconds,
seconds, minutes, or hours. Reasoning remains in the body below the breadcrumb,
rendered as a Markdown blockquote under a `### Reasoning` heading.

## Runner callback APIs

Core harness:

- `LlmStepRunnerCallback`: `stepStarted`, `responseUnparseable`, `stepCompleted`;
- `ScenarioExecutionCallback`: `scenarioStarted`, `scenarioAborted`,
  `scenarioCompleted`;
- `PlainLlmLogger` / `MarkdownLlmLogger`;
- `PlainScenarioLogger` / `MarkdownScenarioLogger`.

Evaluation module:

- `EvaluationStepRunnerCallback`: `evaluationStarted`, `evaluationSkipped`,
  `evaluationFailed`, `evaluationCompleted`;
- `PlainEvaluationLogger` / `MarkdownEvaluationLogger`.

The runners emit domain events only. Their generic callback collaborators can be
used for logging, metrics, tracing, or other observation; the shipped logger
implementations own all text, Markdown, marker, and level decisions.
`LlmStepRunnerCallback.stepStarted` combines the actor status breadcrumb with the
complete step heading and instruction, avoiding a duplicate scenario event.
Existing convenience constructors remain source-compatible; standalone
`ScenarioExecutor(StepRunner)` stays silent while the starter supplies the selected
callback implementation.

## Scenario lifecycle

`ScenarioExecutor` injects only `ScenarioExecutionCallback` into
`ScenarioExecution`; model state is not part of the execution lifecycle. This
common seam serves both `execute(...)` and JUnit's step-at-a-time path:

- scenario start occurs once, on the first `next()`;
- scenario start occurs immediately before the first `StepRunner.run(...)`;
- `LlmStepRunner` emits its combined step-start event immediately before the
  actor request;
- verdict failures produce a normal `scenarioCompleted(FAIL)`;
- transport/infrastructure exceptions produce `scenarioAborted` with the partial
  result, then propagate unchanged;
- successful and fail-fast executions produce exactly one completion event.

## Configured and actual model information

`ModelRegistry` stores non-sensitive configuration independently for actor and
judge roles:

- configured model and sanitized base URL;
- temperature and top-p;
- max tokens / max completion tokens;
- reasoning effort;
- first server-reported actual model.

Actor settings come from `ChatModel.getOptions()` plus Spring OpenAI base-URL
property precedence. Judge settings come from evaluation configuration. URL user
info, query parameters, and fragments are removed; invalid sensitive URLs are
replaced with a redacted sentinel. These settings remain registry metadata and
are not rendered as a logging preamble.

Actual metadata is not fetched at configuration time and no probe request is
sent. `ModelMetadataAdvisor` observes the real actor and judge `ChatClient`
responses, keeps looking past blank metadata, and atomically stores the first
usable model per role. `MarkdownLlmLogger` receives `ModelRegistry` directly and
puts the actual actor name first in its breadcrumb when available. Full model paths
are reduced to their filename and a case-insensitive `.gguf` suffix is removed.
The first step start usually omits it; the first completion and later breadcrumbs
include it. No probe, model preamble, or pending/unresolved label is emitted.

## Starter wiring

`inquisitor-harness-starter` always provides replaceable `ModelRegistry`,
`LlmStepRunnerCallback`, and `ScenarioExecutionCallback` beans. It selects implementations from
`inquisitor.harness.logging.format`, attaches the actor metadata advisor, and
injects the collaborators into `LlmStepRunner` and `ScenarioExecutor`.

When evaluation is enabled, `inquisitor-harness-evaluation-starter` provides the
matching `EvaluationStepRunnerCallback`, registers judge configuration, and
attaches a judge metadata advisor to the bare judge client. Consumers can replace
any callback with their own bean.

## Rendering choices

- no renderer dependency: raw Markdown remains readable;
- `inquisitor-logback-markdown`: manual `%mdMsg{marked}` Logback integration;
- `inquisitor-logback-markdown-starter`: automatic rendering for compatible
  Spring Boot console layouts.

The core/evaluation harness modules depend on neither Markdown renderer and work
with any SLF4J backend.

## Tests

- actor verdict conversion and semantic lifecycle events;
- judge start/skip/failure/completion events without changing actor outcomes;
- whole-run and step-at-a-time scenario ordering, completion, and abort behavior;
- marker and outer-newline behavior for Markdown loggers, plus unmarked plain logs;
- first-nonblank response metadata caching and breadcrumb timing;
- format property binding and replaceable callback beans;
- generic/OpenAI option mapping, URL precedence and sanitization;
- actor/judge registry entries when evaluation is enabled;
- outer-newline preservation in the Logback Markdown converter.

## Verification

```shell
./gradlew :inquisitor-harness:test \
  :inquisitor-harness-starter:test \
  :inquisitor-harness-evaluation:test \
  :inquisitor-harness-evaluation-starter:test \
  :inquisitor-logback-markdown:test
```
