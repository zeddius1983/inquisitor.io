# Task 15E — Code-block panels and syntax highlighting

> Status: **✅ done.** Builds on task 15A's reusable terminal renderer and is
> automatically available through task 15B's Spring Boot starter.

Improve fenced and indented code readability without changing stored Markdown
or requiring a heavyweight syntax package.

## Behavior

When ANSI output is enabled:

- render each code block on a bright-black background;
- add one inner space on the left and right;
- pad every row, including blank rows, to the longest source line;
- keep the panel aligned inside lists and block quotes;
- read the first fenced-code info word and apply language-aware token colors;
- reset the background at every line boundary and restore it after each token.

When ANSI is disabled, emit the original code text without panel padding or
escape sequences.

## Highlighting

`SyntaxHighlighter` is a public, thread-safe extension point. It returns ordered
source spans with semantic styles: plain, keyword, string, number, comment,
property, variable, and operator.

`BuiltinSyntaxHighlighter` supports:

- JSON/JSONC;
- SQL, PostgreSQL, and MySQL;
- HTTP request lines, response status lines, and headers;
- Java;
- bash/sh/shell/zsh.

The built-in implementation is deliberately lightweight and line-oriented. It
adds no new dependency to the published renderer module. Unknown languages fall
back to plain code.

## Safety and performance

- Highlight only while ANSI output is active.
- Skip highlighting for code blocks over 16 KiB.
- Copy custom highlighter results before use.
- Verify that concatenated span text equals the exact input line.
- Fall back to the base code style when a highlighter throws or changes content.
- Keep the built-in highlighter stateless so one renderer remains safe for
  concurrent logging calls.

## Tests

- Built-in JSON, SQL, HTTP, Java, and shell token classification.
- Exact source reconstruction for highlighted output.
- Equal-width ANSI panels with visible background on short and blank lines.
- Fence language propagation to custom highlighters.
- Corrupt custom highlighter output falls back without changing source.
- ANSI-disabled rendering remains byte-for-byte readable code.

## Verification

```shell
./gradlew :inquisitor-logback-markdown:test \
  :inquisitor-logback-markdown-starter:test
./gradlew check
```
