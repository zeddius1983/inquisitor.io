# Task 15F — Clean marker-only Markdown console

> Status: **✅ done.** Builds on task 15B's automatic starter and task 15C's
> marker-tagged semantic harness events.

When `inquisitor.harness.logging.format=markdown`, compatible Logback console
appenders should display a self-contained Markdown narrative rather than wrapping
it in Spring Boot's conventional timestamp/thread/logger prefix.

## Behavior

- Add a full-event `%mdEvent` converter that emits only
  `INQUISITOR_MARKDOWN` events.
- Render a compact `HH:mm:ss.SSSLEVEL` header before the Markdown body.
- Style time with the neutral Gruvbox `color_bg1`/`color_fg0` pair; INFO green;
  WARN yellow; ERROR red; DEBUG purple; TRACE gray.
- Reuse one selected true-color palette for breadcrumbs, Markdown text, tables,
  and syntax-highlighted code. Gruvbox Dark is the default; Nord, Catppuccin
  Mocha, and Tokyo Night are also built in.
- Keep the timestamp header flush left, indent rendered content four spaces, and
  place a blank line before and after the timestamp header.
- Preserve marked throwable output and renderer-failure fallback behavior.
- Emit zero bytes for unmarked events, including no orphan newline.

## Automatic starter integration

- Add `inquisitor.logging.markdown.console-mode=auto|shared|exclusive`.
- Add `inquisitor.logging.markdown.palette=gruvbox|nord|catppuccin|tokyo-night`.
- Allow third-party named palettes through the base module's early-startup-safe
  `MarkdownPaletteProvider` ServiceLoader SPI.
- `auto` resolves to `exclusive` for a Markdown harness and `shared` otherwise.
- Shared mode keeps task 15B's instance-local message-converter replacement.
- Exclusive mode replaces only compatible console patterns with `%mdEvent`;
  file, JSON, custom, and unsupported appenders remain untouched.
- Install exclusive mode immediately after Boot's logging initialization so
  ordinary Spring startup events are suppressed as well.
- Install an idempotent marker-aware TurboFilter that ACCEPTs Markdown events and
  returns NEUTRAL for everything else. This makes marked DEBUG/TRACE events visible
  without broad package logging levels or suppressing ordinary file logs.

## Selection contract

Use the existing SLF4J event marker rather than a Java `MarkdownLogger` marker
interface. Logback receives `ILoggingEvent`, not the Spring bean instance that
emitted it; class loading or logger-name reflection would be brittle and would
exclude custom marker-aware emitters.

## Acceptance

A Markdown harness run with the automatic starter needs no `logging.level`
overrides, produces only marked Markdown events on compatible consoles, shows the
semantic Powerline time/level header, leaves non-console appenders unchanged, and
passes deterministic ANSI/plain, marker filtering, idempotence, and fallback tests.
