# Inquisitor Logback Markdown

`inquisitor-logback-markdown` adds a Flexmark-backed `%mdMsg` conversion word to
Logback pattern layouts. It renders headings, emphasis, code, lists, quotes, and
links as terminal-oriented text with ANSI styling. It has no dependency on the
Inquisitor harness or Spring.

Every non-empty rendered line receives four spaces of left padding. Padding is
applied after Markdown parsing, so it visually separates Markdown from ordinary
log lines without turning the source into a Markdown code block. Blank lines
remain empty and unmarked messages remain byte-for-byte unchanged. List markers
receive one additional space, for a five-space total indent in converted output;
continuation and nested-list content remains aligned with its marker.
Leading and trailing source line breaks are preserved by count, so callers can
request a genuine blank line between the conventional Logback prefix and a
rendered Markdown block.

## Markdown tables

GFM pipe tables render as compact Rich-style Unicode tables. For example:

```markdown
| Model | Groundedness | Notes |
|:------|-------------:|:------|
| gemma-3 | **100%** | Fast |
| qwen-3 | 92% | Uses `reasoning` |
```

An ANSI-capable console uses dim borders, a bold cyan header, and the normal
inline Markdown styles while retaining this geometry:

```text
┌─────────┬──────────────┬────────────────┐
│ Model   │ Groundedness │ Notes          │
├─────────┼──────────────┼────────────────┤
│ gemma-3 │         100% │ Fast           │
│ qwen-3  │          92% │ Uses reasoning │
└─────────┴──────────────┴────────────────┘
```

ANSI-disabled output has the same Unicode shape with no escape sequences.
Left, centre, and right separator alignment is honoured; narrow cells wrap on
word boundaries and hard-wrap long tokens without discarding content. Width is
measured in terminal columns, so combining marks, CJK text, and emoji do not
distort the borders.

The deterministic message-body limit defaults to 120 columns. Manual Logback
patterns can override it in marked or unmarked mode:

```xml
<pattern>%mdMsg{marked,tableWidth=100}%n</pattern>
<pattern>%mdMsg{tableWidth=100}%n</pattern>
```

For converter output, the limit includes the standard four-space Markdown
indent. It cannot discover the timestamp/logger prefix outside `%mdMsg`; subtract
that prefix from the desired physical terminal width. Invalid or out-of-range
values fall back to 120 and produce a Logback status warning. Programmatic users
can configure the renderer directly:

```java
new FlexmarkAnsiRenderer(true, new MarkdownRendererOptions(100));
```

Tables are a console presentation. File and structured/JSON appenders should
continue using `%msg` so they retain the original Markdown source.

## Code blocks

When ANSI is enabled, fenced and indented code blocks render as dark background
panels. Every row receives one inner space on each side and is padded to the
longest source line, so blank and short lines retain the same rectangular frame.
When ANSI is disabled, code remains ordinary unframed text.

The default renderer reads the first fenced-code info word and highlights:

- `json` / `jsonc`;
- `sql`, including PostgreSQL and MySQL aliases;
- `http` / `https` request and response snippets;
- `java`;
- `bash`, `sh`, `shell`, and `zsh`.

Unknown languages use the normal code style. Highlighting is capped for large
blocks, operates without an additional runtime dependency, and never controls
whether the log message succeeds. The renderer verifies that highlighted spans
reconstruct the exact source line; invalid output or a highlighter exception
falls back to unhighlighted code.

Applications can supply a thread-safe custom `SyntaxHighlighter` through
`new FlexmarkAnsiRenderer(ansiEnabled, highlighter)`. The programmatic renderer
can then be passed to `MarkdownMessageConverter`; Spring Boot applications can
replace the starter's `MarkdownRenderer` bean.

## Powerline breadcrumbs

An ANSI-rendered heading that uses Powerline caps and separators is displayed as
a Powerlevel10k-style sequence of background-colored pills:

```markdown
###  Accounts scenario  Verify balances  ▰▰▰▰▰ 4/4  PASS  ⧖ 13.289 s 
```

Each transition uses the preceding segment's foreground color and the following
segment's background color, producing a continuous Powerline join. The built-in
renderer colors known lifecycle and evaluation-result segments semantically:
green for active/successful states, yellow for partial/skipped states, and red
for failed/unsupported states. Other segments use the positional palette. Plain
rendering preserves the same readable text without ANSI sequences, while headings
without this exact shape retain the normal heading style.

Breadcrumbs use the rounded Powerline `` and `` caps with `` transitions.
They require a patched font that includes the Powerline extra-symbol range.
The harness uses a five-cell `▰`/`▱` gauge, rounded to the nearest cell, before
the current/total step counter. Completion breadcrumbs add the verdict and a
human-readable `⧖` duration segment.

## Dependency

```kotlin
dependencies {
    implementation(platform("io.inquisitor:inquisitor-bom:<version>"))
    implementation("io.inquisitor:inquisitor-logback-markdown")
}
```

The module exposes Logback types as a compile-only API and does not select or
pin an application's logging backend at runtime. Spring Boot applications
already receive a compatible `logback-classic` through starter logging. A
standalone Logback application must provide `logback-classic` 1.5.13 or later
itself.

## Shared console appender

Spring Boot users who prefer zero XML configuration can instead depend on
`inquisitor-logback-markdown-starter`. It installs marker-aware rendering into
compatible pattern console appenders while leaving file, structured, and
unsupported appenders unchanged. See the
[starter README](../inquisitor-logback-markdown-starter/README.md).

Include the bundled conversion rule and use `%mdMsg{marked}` in the console
pattern. Only events carrying the `INQUISITOR_MARKDOWN` marker are rendered;
all other messages remain byte-for-byte unchanged.

The harness emits that marker automatically when
`inquisitor.harness.logging.format=markdown`, so its scenario, step, actor, and
judge Markdown works with the same configuration without coupling the harness
to this module.

For Spring Boot, use `logback-spring.xml`:

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

The same include and appender work in plain `logback.xml`; omit the Spring Boot
`defaults.xml` include:

```xml
<configuration>
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

Mark Markdown events through the normal SLF4J API:

```java
import static io.inquisitor.logback.markdown.MarkdownMarkers.markdown;

log.info(markdown(), "## Step 1\n\n**Intent:** create an account");
```

## Dedicated Markdown logger

`%mdMsg` without the `marked` option renders every event routed through the
appender. A dedicated non-additive logger is convenient when a component emits
only Markdown:

```xml
<appender name="MARKDOWN_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%mdMsg%n</pattern>
    </encoder>
</appender>

<logger name="example.scenario" level="INFO" additivity="false">
    <appender-ref ref="MARKDOWN_CONSOLE"/>
</logger>
```

Unmarked mode deliberately parses every message as Markdown on the logging
thread. Keep it on a dedicated logger: ordinary messages containing Markdown
punctuation may otherwise be reformatted, and every event would pay the parse
cost. Prefer `%mdMsg{marked}` on a shared console.

Keep `%msg` on file and structured appenders so stored logs retain the original
Markdown source:

```xml
<appender name="FILE" class="ch.qos.logback.core.FileAppender">
    <file>application.log</file>
    <encoder>
        <pattern>%d %-5level %logger - %msg%n</pattern>
    </encoder>
</appender>
```

## ANSI policy

The default `%mdMsg` policy emits ANSI only when all of these are true:

- `System.console()` is available;
- `NO_COLOR` is absent;
- `TERM` is not `dumb`;
- Jansi has not been disabled.

This conservative default keeps redirected output and typical CI/file output
plain. Pattern options can override detection:

```xml
<!-- Force plain text; options are order-independent. -->
<pattern>%mdMsg{marked,plain}%n</pattern>

<!-- Force ANSI for a console whose TTY cannot be detected. -->
<pattern>%mdMsg{marked,ansi}%n</pattern>
```

For a process-wide plain-text switch, set
`-Dorg.jline.jansi.Ansi.disable=true` before the JVM starts. Do not rely on
`Ansi.setEnabled(false)` for Logback configuration: it is thread-local and may
not affect the thread that initializes or uses the converter.

Spring Boot's `spring.output.ansi.enabled` controls Boot's own `%clr`
converter; it does not directly configure manual `%mdMsg` patterns. Select
`plain` or `ansi` explicitly when those policies must agree. Task 15B's
automatic starter will bridge the Boot policy programmatically.

Never use ANSI mode in JSON encoders. File appenders should retain ordinary
`%msg`; `%mdMsg{plain}` is available only when a rendered plain-text file is
intentional.

## Custom rendering

`MarkdownRenderer` is a public functional interface, and
`MarkdownMessageConverter(MarkdownRenderer)` supports programmatic integration
with another terminal renderer. A custom renderer owns its ANSI policy; the
`plain` and `ansi` pattern options configure only the built-in renderer.
