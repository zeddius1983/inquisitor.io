# Inquisitor Logback Markdown

`inquisitor-logback-markdown` adds a Flexmark-backed `%mdMsg` conversion word to
Logback pattern layouts. It renders headings, emphasis, code, lists, quotes, and
links as terminal-oriented text with ANSI styling. It has no dependency on the
Inquisitor harness or Spring.

## Dependency

```kotlin
dependencies {
    implementation(platform("io.inquisitor:inquisitor-bom:<version>"))
    implementation("io.inquisitor:inquisitor-logback-markdown")
}
```

## Shared console appender

Include the bundled conversion rule and use `%mdMsg{marked}` in the console
pattern. Only events carrying the `INQUISITOR_MARKDOWN` marker are rendered;
all other messages remain byte-for-byte unchanged.

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
