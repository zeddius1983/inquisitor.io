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

ANSI output follows Jansi's enabled state when the converter is created. An
application can disable styles before Logback initialization with
`org.jline.jansi.Ansi.setEnabled(false)`; rendered text then remains readable
without escape sequences. Configure terminal and CI color handling explicitly,
and never use `%mdMsg` in JSON encoders.
