# Inquisitor Logback Markdown Starter

`inquisitor-logback-markdown-starter` automatically installs marker-aware
Markdown rendering into compatible Spring Boot Logback console appenders. It
uses the reusable `inquisitor-logback-markdown` renderer and does not depend on
the Inquisitor harness.

## Dependency

```kotlin
dependencies {
    implementation(platform("io.inquisitor:inquisitor-bom:<version>"))
    implementation("io.inquisitor:inquisitor-logback-markdown-starter")
}
```

No `logback.xml` or `logback-spring.xml` change is required for Spring Boot's
standard pattern console appender. Emit Markdown with the shared marker:

```java
import static io.inquisitor.logback.markdown.MarkdownMarkers.markdown;

log.info(markdown(), "## Step 1\n\n**Intent:** create an account");
```

Only marked events are parsed. Ordinary Spring, SQL, and application messages
remain unchanged. Each non-empty rendered Markdown line receives four spaces of
left padding, making multiline blocks visually distinct from their Logback prefix
and surrounding conventional messages. List markers receive one additional space.
ANSI code blocks use the base renderer's equal-width background panels and
language-aware highlighting; plain output remains unframed and readable.

The harness emits the same marker for scenario, step, actor, and judge events when
`inquisitor.harness.logging.format=markdown`; adding this starter renders them
without any logging XML changes.

## Configuration

Starter presence enables compatible console integration by default:

```yaml
inquisitor:
  logging:
    markdown:
      enabled: true
```

Set `enabled: false` to leave the active Logback context untouched.

## ANSI policy

The starter follows Spring Boot's resolved `spring.output.ansi.enabled` policy:

- `always` forces styled output, including test/CI processes without a detectable
  console;
- `never` produces rendered plain text without escape sequences;
- `detect` uses the renderer's conservative terminal detection, respecting
  `NO_COLOR`, `TERM=dumb`, and Jansi's disable property.

This bridge is starter-specific. Manual `%mdMsg` layouts use the base module's
`plain` and `ansi` converter options.

## Supported layouts and fallback

The starter updates only `ConsoleAppender` instances whose encoder exposes a
Logback `PatternLayout`. It keeps the consumer's complete pattern and replaces
the instance-local `m`, `msg`, and `message` converter suppliers with a
marker-aware converter. Attached composite appenders are traversed recursively,
so a compatible console behind an `AsyncAppender` is supported.

It deliberately skips:

- file appenders;
- Spring Boot structured JSON console encoders;
- custom encoders or layouts that do not expose `PatternLayout`;
- consoles created dynamically by `SiftingAppender`;
- non-Logback SLF4J backends.

Unsupported configurations log only a DEBUG diagnostic and never prevent
application startup. Installation is idempotent and shared appenders are
processed once. `SiftingAppender` children are not discoverable when the starter
runs and may be created later, so use the base module's manual
`%mdMsg{marked}` integration inside a sifted appender configuration.

Logback runtime scan/reload may replace the layout and remove the instance-level
override. Restart the application after a logging reconfiguration, or use the
base module's manual `%mdMsg{marked}` integration when runtime scanning is
required.

## Manual alternative

For complete appender and pattern control, depend only on
`inquisitor-logback-markdown` and follow its README to register
`%mdMsg`/`%mdMsg{marked}`. The manual and automatic artifacts are alternatives;
the starter does not ship or take ownership of a root logging configuration.
