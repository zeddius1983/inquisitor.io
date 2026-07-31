# Task 15B — Automatic Logback Markdown starter

> Status: **✅ done.** Implemented as the optional
> `inquisitor-logback-markdown-starter` module with marker-only instance-local
> converter installation, Spring Boot ANSI-policy bridging, compatibility
> fallbacks, and focused autoconfiguration/Logback tests.

Build `inquisitor-logback-markdown-starter`, an optional Spring Boot starter that
installs task 15A's marker-aware Markdown conversion into compatible console
appenders without requiring `logback-spring.xml` changes.

The manual path remains first-class: users who want complete layout control add
only `inquisitor-logback-markdown` and follow task 15A's XML documentation. Users
who prefer convention over configuration add this starter instead.

## Module and dependency shape

Create `inquisitor-logback-markdown-starter` with base package
`io.inquisitor.logback.markdown.autoconfigure`.

- Depend on `inquisitor-logback-markdown` and Spring Boot autoconfiguration.
- Add the module to `settings.gradle.kts`, the BOM, and the standard
  `AutoConfiguration.imports` resource.
- Guard everything with Logback/Spring Boot classpath conditions so an
  application using another logging backend still starts.
- Do not depend on `inquisitor-harness`; this starter is reusable by any Spring
  Boot application.

## Lifecycle decision

Spring Boot initializes the logging system before ordinary application-context
autoconfiguration. The starter must not try to win that bootstrap race by
shipping a root logging configuration or replacing `logging.config`.

Instead, after Logback has initialized and before application/harness work runs,
install the converter into the already-created compatible console layouts:

1. obtain the active `LoggerContext`;
2. discover console appenders reachable from root and named loggers, recursively
   traverse `AppenderAttachable` composites, and de-duplicate by identity;
3. select appenders backed by a Logback `PatternLayout`;
4. add marker-aware converter suppliers to that layout instance for the standard
   message words (`m`, `msg`, and `message`);
5. safely stop/recompile/start that layout under the context configuration lock;
6. leave every other part of the user's pattern intact.

Using `PatternLayout#getInstanceConverterMap()` keeps the override local to the
specific console layout. Do not modify `PatternLayout`'s global static converter
map or the `LoggerContext`-wide rule registry: either could unexpectedly affect
file appenders or later-created layouts.

Because the replacement converter is marker-aware, normal Spring, SQL, and
application messages still return their original formatted text. Only events
carrying `INQUISITOR_MARKDOWN` are parsed and rendered.

The starter must bridge Spring Boot's ANSI policy explicitly. Manual
`%mdMsg` uses task 15A's terminal detection and `plain`/`ansi` options, while
Boot's `spring.output.ansi.enabled` controls a separate `AnsiOutput` state.
When installing converter suppliers programmatically, construct the renderer
from Boot's resolved `AnsiOutput` policy (always/never/detect) rather than
relying on Jansi's thread-local state. In detect mode, delegate to task 15A's
conservative terminal detection.

## Compatibility and fallback

- Plain `ConsoleAppender` + `PatternLayout` is the MVP supported surface.
- Compatible consoles behind `AppenderAttachable` composites such as
  `AsyncAppender` are supported; dynamically created `SiftingAppender` children
  require manual converter configuration.
- Skip structured JSON encoders: Markdown/ANSI has no place inside JSON output.
- Skip unsupported custom encoders/layouts without replacing the user's
  appender.
- Log one concise DEBUG diagnostic for skipped/unsupported layouts; never fail
  application startup merely because pretty rendering cannot be installed.
- Installation must be idempotent across repeated lifecycle callbacks or shared
  appenders.
- Logback scan/reload support is out of scope for the first version; document
  that a runtime reconfiguration may remove the instance-level override.
- If ANSI is disabled/not supported, produce rendered readable plain text rather
  than injecting escape codes.

## Configuration

Starter presence enables automatic integration by default:

```yaml
inquisitor:
  logging:
    markdown:
      enabled: true
```

`enabled=false` makes no changes to the active `LoggerContext`. Keep the property
namespace owned by the logging feature, not by the harness.

Additional theme/style properties are deferred until task 15A proves that more
than one stable presentation is useful.

## Tests

- Application-context test with Logback's real `PatternLayoutEncoder` console
  path: marked Markdown is rendered without user XML.
- An existing custom console pattern keeps its timestamp/level/logger structure;
  only the message converter changes.
- Unmarked messages are identical before and after installation.
- File appenders remain on the ordinary message converter and receive raw
  Markdown.
- Structured console encoders are skipped.
- Unsupported custom encoders are skipped without startup failure.
- `enabled=false` performs no mutation.
- No Logback classes / an alternative backend: autoconfiguration backs off.
- Repeated installation is idempotent and a shared appender is processed once.
- `spring.output.ansi.enabled=never` produces no escape bytes, while `always`
  forces styles even when the test JVM has no console.
- A simulated layout-recompile failure restores the original converter map and
  does not escape the installer.

Tests that mutate a real `LoggerContext` must restore it and any global ANSI
state so parallel tests and later suites are not contaminated.

The implementation injects a small internal logging-system provider into the
startup callback. Production resolves `LoggerFactory.getILoggerFactory()`;
tests supply an isolated `LoggerContext`, proving the lifecycle without
mutating the JVM's real logging configuration.

## Documentation

The README for this module must present the choice explicitly:

1. **Manual:** depend on `inquisitor-logback-markdown`, include the conversion
   rule, and select `%mdMsg`/`%mdMsg{marked}` in `logback.xml` or
   `logback-spring.xml`.
2. **Automatic:** depend on `inquisitor-logback-markdown-starter`; no Logback XML
   change for supported console layouts.

Document the fallback behavior and the unsupported structured/custom-layout
cases. Never imply that terminals natively render Markdown: the converter parses
Markdown and emits ANSI-styled terminal text.

## Non-goals

- No semantic harness logging or model metadata — task 15C.
- No takeover of the root logger, console pattern, `logging.config`, or consumer
  logging files.
- No ANSI rendering in file or structured appenders.
- No support for Log4j2/JUL in this artifact family.

## Acceptance

Adding `inquisitor-logback-markdown-starter` to a standard Spring Boot + Logback
application makes marker-tagged Markdown render through the existing pattern
console appender with no XML changes, while removing/disabling the starter
restores ordinary raw logging and all unsupported configurations degrade safely.
