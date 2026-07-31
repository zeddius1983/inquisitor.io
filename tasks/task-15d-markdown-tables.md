# Task 15D — Rich-style Markdown tables in terminal logs

> Status: **✅ done.** Depends on task 15A's reusable renderer. It is
> independent of the task 15B starter and task 15C semantic harness logging: every
> Markdown message rendered by `%mdMsg` can benefit from table support.

Extend `inquisitor-logback-markdown` so standard pipe-delimited Markdown tables
render as readable terminal tables rather than flattened cell text. The target
is the useful part of Rich's presentation: clear Unicode borders, a distinct
header, Markdown alignment, styled inline cell content, and width-aware wrapping.

Given:

```markdown
| Model | Groundedness | Notes |
|:------|-------------:|:------|
| gemma-3 | **100%** | Fast |
| qwen-3 | 92% | Uses `reasoning` |
```

the plain-text shape is:

```text
┌─────────┬──────────────┬────────────────┐
│ Model   │ Groundedness │ Notes          │
├─────────┼──────────────┼────────────────┤
│ gemma-3 │         100% │ Fast           │
│ qwen-3  │          92% │ Uses reasoning │
└─────────┴──────────────┴────────────────┘
```

ANSI-capable output styles the border, header, and inline Markdown without
changing that geometry. ANSI-disabled output keeps the same Unicode table and
contains no escape sequences.

## Parsing and supported Markdown

- Add Flexmark's narrow `flexmark-ext-tables` artifact through the version
  catalog and register `TablesExtension` in the shared parser. Keep the module
  on Flexmark core plus this one extension rather than restoring
  `flexmark-all`.
- Handle `TableBlock`, head/body rows, cells, and separator alignment
  structurally. Do not reconstruct tables by splitting source lines on `|`.
- Support ordinary GFM-style tables with optional leading/trailing pipes,
  escaped pipes, empty cells, missing trailing cells, and left/centre/right
  separator alignment.
- Preserve inline Markdown inside cells: text, emphasis, strong emphasis,
  inline code, and links use the same theme as content outside tables.
- Normalize uneven rows to the table's column count without dropping cell
  content. Extra cells recognized by Flexmark must remain visible.
- A table inside a block quote or list keeps the active prefix/indentation, and
  that prefix is deducted from the available table width.

## Rendering architecture

Do not append a table directly from individual Flexmark visitor callbacks.
Collect each `TableBlock` into a small immutable internal model first, then pass
it to a dedicated terminal table renderer.

The internal model records:

- header and body rows;
- cell inline fragments and their styles;
- column alignment;
- visible display width independently of emitted ANSI bytes.

Table rendering then proceeds in three stages:

1. measure unwrapped cell fragments in terminal columns;
2. allocate column widths and wrap cells to the configured message-body width;
3. emit borders, padding, text alignment, and ANSI styles.

Keeping measurement ahead of emission prevents ANSI escape sequences from
inflating column widths and avoids stripping/re-parsing already-rendered text.
Mutable row/layout state belongs to one render call so task 15A's concurrency
guarantee remains intact.

## Display width, wrapping, and alignment

- Measure terminal columns, not Java `String.length()`: ANSI controls and
  combining marks are zero-width; ordinary characters are one column; common
  East Asian wide/full-width characters and emoji are two columns.
- Hide the width algorithm behind a small internal `DisplayWidth` abstraction
  and test it directly. If an external width implementation is selected, add
  only the narrow utility dependency through the version catalog; a full
  terminal framework is not required merely to draw tables.
- Include borders and one space of padding on each side of every cell in width
  calculations.
- When the natural table fits, use the natural column widths. Otherwise shrink
  wrappable columns fairly, wrap on word boundaries, and hard-wrap a single long
  token only when necessary.
- A physical row is as tall as its most-wrapped cell; shorter cells receive
  blank lines and all content is top-aligned.
- Left alignment is the default. Honour `:---`, `:---:`, and `---:` for left,
  centre, and right alignment in both header and body cells.
- Never truncate or silently discard data. If the border plus minimum cell
  widths cannot fit because the table has too many columns, prefer a readable
  overflowing table over data loss.

## Width configuration and compatibility

The Logback converter cannot reliably discover how much of the physical console
line remains after a consumer's timestamp/logger prefix. Treat the limit as a
deterministic **message-body width**, defaulting to 120 columns.

- Keep `%mdMsg` and `%mdMsg{marked}` behavior source- and configuration-compatible.
- Add a named converter option for manual layouts, for example
  `%mdMsg{marked,tableWidth=100}` and `%mdMsg{tableWidth=100}`.
- Introduce an immutable renderer options object for programmatic integration,
  retaining the existing `FlexmarkAnsiRenderer()` and
  `FlexmarkAnsiRenderer(boolean)` constructors with current defaults.
- Reject malformed or unreasonably small/large widths safely, fall back to the
  documented default, and report converter setup problems through Logback's
  status mechanism rather than the application log stream.
- Document that users with a long Logback pattern prefix should subtract that
  prefix from the desired physical terminal width.
- Task 15B's automatic starter may initially use the default. A Spring property
  for table width is only added there if the starter is implemented or updated
  in the same delivery; manual XML configuration must not depend on it.

No terminal-size probing is required for this task. It would be unreliable for
redirected output and would make golden tests environment-dependent.

## Visual theme

- Draw tables with deterministic Unicode box characters (`┌─┬┐`, `├─┼┤`, and
  `└─┴┘`) and no border between every body row, matching the compact Rich style.
- Render borders dim and the header bold/cyan using task 15A's ANSI abstraction.
- Retain inline styles in cell content and reset them without losing the table's
  surrounding style. No ANSI state may leak past a cell, row, table, or event.
- Keep layout independent of colour choices so theme changes cannot alter
  wrapping or alignment.

## Tests

- Parser/renderer golden tests for a basic table and optional outer pipes.
- Left, centre, and right alignment with odd and even padding.
- Empty cells, escaped pipes, missing cells, and Flexmark-recognized extra cells.
- Inline strong/emphasis/code/link styles inside cells without geometry drift.
- ANSI-enabled and ANSI-disabled output have identical visible geometry.
- Narrow-width wrapping, multi-line row height, a long unbroken token, and the
  too-many-columns overflow fallback retain all source content.
- ASCII, combining characters, CJK text, and representative emoji align by
  terminal display width rather than UTF-16 length.
- Nested table prefixes/indentation are included consistently.
- Malformed `tableWidth` options fall back safely; existing `marked` option
  semantics remain unchanged.
- Concurrent table renders do not share row, width, or style state.
- End-to-end `LoggerContext` + `%mdMsg` coverage proves a Markdown table renders
  through the bundled conversion rule while `%msg` still emits the raw table.

Golden expectations should make the table shape obvious, while focused semantic
assertions should cover ANSI styles so colour tweaks do not rewrite every fixture.

## Documentation

- Add a table example to the module README showing raw Markdown, ANSI-capable
  console output, and the ANSI-disabled shape.
- Document the default message-body width and both converter option forms.
- Reiterate that file/JSON appenders should keep `%msg`; Unicode borders and ANSI
  presentation are console concerns.
- Link this task from task 15A as the owner of table layout.

## Non-goals

- No HTML `<table>` rendering.
- No row spans, column spans, nested tables, or Flexmark table captions in the
  first version; ordinary GFM-style tables are the compatibility target.
- No interactive horizontal scrolling, terminal cursor control, or live table
  updates.
- No automatic terminal-width discovery.
- No syntax highlighting inside cells and no attempt at exact Rich/Glow parity.
- No changes to harness scenario/model logging; task 15C remains its owner.

## Acceptance

A standard Markdown table passed through `%mdMsg` or `%mdMsg{marked}` renders as
a bordered, aligned, width-aware terminal table; inline Markdown remains styled;
Unicode and ANSI do not corrupt column geometry; narrow tables wrap without data
loss; existing non-table rendering, marker filtering, raw `%msg` output, and
concurrent use remain green.
