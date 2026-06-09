# Task 02 — `inquisitor-core` Module

## Goal

Implement the pure-Java kernel of the framework. Zero Spring, zero AI, zero HTTP dependencies. Defines the domain model and the two key interfaces (`ScenarioParser`, `ScenarioExecutor`) that all other modules depend on.

## Dependencies

- `flexmark-all` (markdown parsing)
- `junit-jupiter` (test scope)

## Package Layout

```
io.inquisitor.core
├── model/
│   ├── Scenario.java          record
│   ├── TestResult.java        record
│   ├── ToolCallRecord.java    record
│   └── ExecutionContext.java  class (mutable builder or immutable record)
├── parser/
│   ├── ScenarioParser.java        interface
│   └── DefaultScenarioParser.java implementation
└── executor/
    └── ScenarioExecutor.java      interface
```

## Types

### `Scenario` record
```java
public record Scenario(
    String name,          // H1 title from markdown, or filename if absent
    String description,   // first non-blank paragraph
    String rawMarkdown    // full original content, unchanged
) {}
```

### `TestResult` record
```java
public record TestResult(
    boolean passed,
    String verdict,           // one-line summary
    String reasoning,         // LLM's full explanation
    List<ToolCallRecord> toolCalls
) {}
```

### `ToolCallRecord` record
```java
public record ToolCallRecord(
    String toolName,
    Map<String, Object> args,
    Object result,
    Duration elapsed
) {}
```

### `ExecutionContext`
```java
public final class ExecutionContext {
    private final String baseUrl;
    private final DataSource dataSource;       // nullable
    private final Map<String, Object> extras;  // arbitrary extension points

    // static factory / builder
    public static Builder builder() { ... }
}
```

### `ScenarioParser` interface
```java
public interface ScenarioParser {
    Scenario parse(String markdownContent);
}
```

### `DefaultScenarioParser`
Uses Flexmark `Parser` + `Document` AST:
- Walk heading nodes → first `Heading` of level 1 → `name`
- Walk paragraph nodes → first `Paragraph` text → `description`
- `rawMarkdown` = the original string verbatim

### `ScenarioExecutor` interface
```java
public interface ScenarioExecutor {
    TestResult execute(Scenario scenario, ExecutionContext context);
}
```

## Unit Tests

`DefaultScenarioParserTest`:
- `parsesH1TitleAsName()` — markdown with `# My Scenario` → name = "My Scenario"
- `usesFilenameWhenNoH1()` — markdown with only `## Section` → name falls back to empty string (or caller-supplied default)
- `extractsFirstParagraphAsDescription()` — multi-section doc, description = first para only
- `preservesRawMarkdown()` — `rawMarkdown` equals input verbatim including whitespace
- `handlesEmptyDocument()` — blank string → `Scenario("", "", "")`

## Verification

```bash
./gradlew :inquisitor-core:test
# All 5 unit tests green, no Spring context needed
```

## Notes / Open Questions

- Should `ExecutionContext` be a `record` (immutable) or a mutable class with setters for easier Spring `@Bean` wiring? Lean toward immutable record + builder.
- `DataSource` import pulls in `javax.sql` — acceptable in a "pure Java" module since it's a JDK standard library (no third-party dep).
- Flexmark version 0.64.8 — confirm it has a module-info or at least doesn't conflict with Java 26 strong encapsulation.
