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
import static io.inquisitor.logback.markdown.marker.MarkdownMarkers.markdown;

log.info(markdown(), "## Step 1\n\n**Intent:** create an account");
```

Only marked events are parsed. In shared mode, ordinary Spring, SQL, and
application messages remain unchanged and each rendered Markdown line receives
four spaces of left padding. In exclusive mode the starter owns the complete
console event: ordinary events are omitted, the original Logback prefix is
removed, and an indented Markdown body follows a compact Powerline time/level
header. The exclusive layout is installed in Boot's early logging lifecycle, so
framework startup records are suppressed before application initialization.
ANSI code blocks use the base renderer's equal-width background panels and
language-aware highlighting; plain output remains unframed and readable.

The harness emits the same marker for scenario, step, actor, tool, and judge events
when `inquisitor.harness.logging.format=markdown`. The starter's default `auto`
mode detects that property and selects the exclusive console automatically,
without logging XML or logger-level configuration.

## Configuration

Starter presence enables compatible console integration by default:

```yaml
inquisitor:
  logging:
    markdown:
      enabled: true
      console-mode: auto # auto | shared | exclusive
      palette: gruvbox   # gruvbox | nord | catppuccin | tokyo-night
```

Set `enabled: false` to leave the active Logback context untouched.

`auto` preserves the shared console for a generic application and selects
`exclusive` when `inquisitor.harness.logging.format=markdown`. Set `shared`
explicitly to keep ordinary console logs beside the rendered harness narrative,
or `exclusive` to use the clean marker-only console without the harness.

## Exclusive console

Exclusive mode replaces the compatible console layout with the full-event
`%mdEvent` converter. A marked INFO event appears as:

```text

13:42:15.123INFO

    Fetch a non-existent account
```

ANSI-capable terminals use the selected palette for the timestamp, log level,
Markdown syntax, tables, code highlighting, and Powerline breadcrumbs. Gruvbox
is the default and uses the neutral `color_bg1` timestamp background
(`#3c3836`) and a level-specific segment:

| Level | Color |
|-------|-------|
| INFO | muted green `#98971a` |
| WARN | muted yellow `#d79921` |
| ERROR | muted red `#cc241d` |
| DEBUG | muted purple `#b16286` |
| TRACE | neutral `#665c54` |

With the default, breadcrumbs and Markdown syntax use the rest of Gruvbox Dark:
orange `#d65d0e`, aqua `#689d6a`, blue `#458588`, foreground `#fbf1c7`, and
neutral panel shades `#3c3836`/`#665c54`.

The other built-in choices are Nord, Catppuccin Mocha, and Tokyo Night. Palette
selection is honored by both shared mode and the early exclusive-console
installation, so startup and application events cannot disagree. Powerline text
automatically switches between the palette's light and dark neutral colors to
maintain contrast against each segment background, falling back to black or white
when necessary to reach 4.5:1.

Third-party palettes implement the base module's `MarkdownPaletteProvider` SPI
and register it through `META-INF/services`. Their provider name can be used in
the same `palette` property. `ServiceLoader` resolution intentionally happens
outside the Spring bean lifecycle so the palette is already available to the
early exclusive-console installer. See the base module's
[custom palette guide](../inquisitor-logback-markdown/README.md#custom-palettes).
An unknown, blank, or broken provider selection logs a warning and falls back to
Gruvbox; a cosmetic palette setting never prevents application startup.

The SLF4J `INQUISITOR_MARKDOWN` marker is the selection contract. A Java marker
interface is intentionally unnecessary: Logback evaluates events, not Spring bean
types, and the event marker also supports custom Markdown emitters. The starter
does not override logger levels or user TurboFilters: a marked event must first pass
normal SLF4J/Logback filtering. The shipped harness Markdown loggers emit at INFO;
custom emitters using DEBUG or TRACE must enable those levels normally. The exclusive
console converter then emits only marked events, while file and structured appenders
retain their configured levels, filters, raw events, and patterns.

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
Logback `PatternLayout`. Shared mode keeps the consumer's complete pattern and
replaces the instance-local `m`, `msg`, and `message` converter suppliers with a
marker-aware converter. Exclusive mode replaces that console pattern with
`%mdEvent`; the original pattern remains untouched on every non-console appender.
Attached composite appenders are traversed recursively, so a compatible console
behind an `AsyncAppender` is supported.

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

Exclusive mode is installed immediately after Spring Boot initializes its logging
system; shared mode and a contributed renderer are finalized by autoconfiguration.
Logback's configuration lock prevents competing reconfiguration but does not pause
threads already formatting events; do not call the installer manually after
application startup.

Logback runtime scan/reload may replace the layout and remove the instance-level
override. Restart the application after a logging reconfiguration, or use the
base module's manual `%mdMsg{marked}` integration when runtime scanning is
required.

## Manual alternative

For complete appender and pattern control, depend only on
`inquisitor-logback-markdown` and follow its README to register
`%mdMsg`/`%mdMsg{marked}` or the full-event `%mdEvent`. The manual and automatic
artifacts are alternatives;
the starter does not ship or take ownership of a root logging configuration.
