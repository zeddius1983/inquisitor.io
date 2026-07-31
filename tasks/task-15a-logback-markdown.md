# Task 15A — Reusable Logback Markdown renderer

> Status: **✅ done.** Implemented as the independent
> `inquisitor-logback-markdown` module with a Flexmark/Jansi renderer, manual
> `%mdMsg` and `%mdMsg{marked}` integration, bundled Logback include, and focused
> unit/concurrency/end-to-end tests.

Build a reusable `inquisitor-logback-markdown` module that renders Markdown log
messages as ANSI-styled console text through a Logback `MessageConverter`. It is
not harness-specific: any application can mark or route a log event as Markdown
and use the converter in a Logback pattern.

## Goals

- Parse Markdown structurally with Flexmark rather than a regex replacement
  chain.
- Expose a Logback conversion word (`%mdMsg`) for manual integration.
- Preserve the original formatted message as a safe fallback.
- Add four spaces of left padding to each non-empty rendered line, after parsing.
- Indent list markers by one additional space while preserving continuation alignment.
- Keep rendering local to appenders that opt into the converter; file and
  structured appenders can continue using `%msg` unchanged.
- Establish a small, tested rendering surface before the harness emits any new
  verbose messages.

## Module

Create `inquisitor-logback-markdown` with base package
`io.inquisitor.logback.markdown`.

- Add the module to `settings.gradle.kts` and the published BOM.
- Apply the Java/publishing conventions used by the other library modules.
- Add Flexmark core, Logback Classic, and the selected ANSI utility through the
  version catalog/BOM rather than inline versions. Do not use `flexmark-all`
  when the module only needs the parser/core AST.
- Do not depend on `inquisitor-harness` or any Spring AI module.
- Add `package-info.java` with `@NullMarked`.

Logback is the intentional backend API of this artifact. It must not leak into
the core harness merely because this optional module exists.

## Public integration surface

### `MarkdownMessageConverter`

A Logback `MessageConverter` that:

1. obtains `ILoggingEvent.getFormattedMessage()`;
2. decides whether the event should be rendered;
3. delegates to a Flexmark-backed renderer;
4. returns the raw formatted message if rendering fails.

The converter supports two modes:

- `%mdMsg` — render every message routed through that pattern token. Useful for
  a dedicated Markdown logger/appender.
- `%mdMsg{marked}` — render only events carrying the documented
  `INQUISITOR_MARKDOWN` SLF4J marker; return every other message unchanged. This
  is the safe choice when replacing `%msg` in a shared console pattern and the
  mode the automatic starter will use in task 15B.

The modes accept an additional ANSI policy option:

- no option — emit ANSI only for an interactive terminal, respecting
  `NO_COLOR`, `TERM=dumb`, and Jansi's process property;
- `plain` — force escape-free output;
- `ansi` — force styled output when terminal detection is unavailable.

Options are order-independent, for example `%mdMsg{marked,plain}`.

Expose the marker name (and, if useful, a marker factory/helper) as a tiny
SLF4J-only API so applications can deliberately identify Markdown events.

### `FlexmarkAnsiRenderer`

Keep AST rendering separate from the Logback converter so it can be unit-tested
without constructing logging events. A render call owns its mutable state
(`StringBuilder`, traversal state, list depth); the reusable Flexmark `Parser`
may be shared because `Parser.parse(...)` is thread-safe.

Use `NodeVisitor` with `VisitHandler` instances. Handlers that need their content
must explicitly visit their children: a matching Flexmark handler stops default
child traversal. In particular, paragraphs and list items cannot merely append a
prefix or their text will disappear.

Initial supported nodes:

- headings;
- paragraphs, soft breaks, and hard breaks;
- strong emphasis and emphasis;
- inline code and fenced/indented code blocks;
- bullet and ordered lists, including nesting;
- block quotes;
- links (visible label plus destination when useful);
- autolinks, mail links, images (alt text plus destination), entities, and
  thematic breaks;
- plain text.

Unknown nodes should degrade to readable child text rather than fail the event.
Exact Rich/Glow parity, images, and syntax-highlighted code are not required for
the prototype. Rich-style terminal table layout is a separate follow-on in task
15D; code panels and highlighting follow in task 15E.

## ANSI behavior

- Compose styles through a small abstraction instead of scattering raw escape
  strings through visitor handlers.
- Always reset styles at element boundaries so formatting cannot leak into the
  remainder of the log line or the next event.
- Keep rendering deterministic for tests.
- The manual converter is intended for console appenders. Documentation must
  warn users to retain `%msg` for file/JSON appenders and to configure their
  terminal/CI ANSI policy appropriately.
- If ANSI support is disabled by the selected abstraction, return readable
  plain text rather than escape sequences.
- Document `-Dorg.jline.jansi.Ansi.disable=true` as the reliable process-wide
  Jansi switch; its programmatic setter is thread-local.

## Bundled Logback include

Ship a resource such as `io/inquisitor/logback/markdown.xml`:

```xml
<included>
    <conversionRule
        conversionWord="mdMsg"
        class="io.inquisitor.logback.markdown.MarkdownMessageConverter"/>
</included>
```

Use Logback 1.5's `class` attribute; `converterClass` is accepted only as a
deprecated compatibility alias.

## Manual configuration documentation

Document both plain Logback (`logback.xml`) and the preferred Spring Boot form
(`logback-spring.xml`). The minimal Spring Boot example is:

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="io/inquisitor/logback/markdown.xml"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %mdMsg{marked}%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

Also show a dedicated Markdown logger/appender using `%mdMsg`, and a file
appender using ordinary `%msg` to demonstrate that stored logs remain raw.

## Tests

- Renderer unit tests for every supported AST node and representative nesting.
- Paragraph/list whitespace and indentation tests.
- Fenced code containing Markdown-looking characters is not reinterpreted.
- Unknown leaf nodes preserve their source, and explicit tests cover autolinks,
  images, entities, thematic breaks, escapes, and hard line breaks.
- ANSI reset/no-style-leak test across adjacent elements and messages.
- Raw fallback when rendering throws.
- Marked mode renders marked events and leaves ordinary events byte-for-byte
  unchanged.
- Concurrent render calls do not share mutable traversal state.
- A real `LoggerContext` + `PatternLayoutEncoder` integration test proves the
  bundled conversion rule and `%mdMsg` work end to end.
- A paired assertion proves `%msg` still emits the original Markdown.

Use golden strings for the rendered shape where that makes failures easier to
understand, but keep semantic assertions for ANSI codes so theme tweaks do not
make the whole suite brittle.

## Non-goals

- No Spring Boot autoconfiguration — task 15B owns automatic installation.
- No harness logging changes — task 15C owns scenario/model observability.
- No Markdown table layout — task 15D owns Rich-style terminal tables.
- No global mutation of Logback's static converter map.
- No library-owned root `logback.xml`/`logback-spring.xml` that could take over a
  consumer application's logging configuration.
- No Lombok dependency for this small module: its few immutable fields and
  constructors do not justify another consumer-facing annotation processor.

## Acceptance

With only `inquisitor-logback-markdown` on the classpath and the documented XML
include/pattern, a marked Markdown event renders legibly in an ANSI-capable
console; ordinary, file, and structured messages remain unchanged; all module
tests pass without starting Spring or an LLM.
