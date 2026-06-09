# Task 03 — `inquisitor-spring-ai` Module

## Goal

Implement the Spring AI–powered orchestrator and the built-in tool library. This module bridges the core domain model with Spring AI's `ChatClient` and `@Tool` mechanism to run the LLM agentic loop.

## Dependencies

- `project(":inquisitor-core")`
- `spring-ai-core` (via BOM)
- `spring-web` (for `RestClient` inside `HttpTool`)
- `spring-jdbc` (for `DatabaseTool`, optional at runtime)
- `junit-jupiter` + `spring-test` + `mockito-core` (test scope)

## Package Layout

```
io.inquisitor.ai
├── tool/
│   ├── ToolResponse.java      record
│   ├── HttpTool.java          @Component
│   └── DatabaseTool.java      @Component
└── orchestrator/
    ├── SpringAiOrchestrator.java   implements ScenarioExecutor
    └── OrchestratorProperties.java  @ConfigurationProperties subset (maxTurns, prompt template)
```

## Types

### `ToolResponse` record
```java
public record ToolResponse(
    int status,
    String body,
    Map<String, List<String>> responseHeaders
) {}
```

### `HttpTool`
```java
@Component
public class HttpTool {

    private final RestClient restClient;

    @Tool(description = "Perform an HTTP GET request. Returns status, body, and response headers.")
    public ToolResponse get(
        @ToolParam(description = "Absolute URL to call") String url,
        @ToolParam(description = "Optional request headers as key-value pairs", required = false)
            Map<String, String> headers
    ) { ... }

    @Tool(description = "Perform an HTTP POST request with a JSON body.")
    public ToolResponse post(String url, String jsonBody, Map<String, String> headers) { ... }

    @Tool(description = "Perform an HTTP PUT request with a JSON body.")
    public ToolResponse put(String url, String jsonBody, Map<String, String> headers) { ... }

    @Tool(description = "Perform an HTTP DELETE request.")
    public ToolResponse delete(String url, Map<String, String> headers) { ... }
}
```

### `DatabaseTool`
```java
@Component
@ConditionalOnBean(DataSource.class)   // declared here for clarity; enforced in autoconfigure
public class DatabaseTool {

    @Tool(description = "Execute a read-only SQL query and return results as a list of row maps.")
    public List<Map<String, Object>> executeSql(
        @ToolParam(description = "SQL SELECT statement to execute") String sql
    ) { ... }
}
```

### `SpringAiOrchestrator`
```java
public class SpringAiOrchestrator implements ScenarioExecutor {

    private final ChatClient chatClient;
    private final List<Object> tools;          // HttpTool, DatabaseTool, etc.
    private final OrchestratorProperties props;

    @Override
    public TestResult execute(Scenario scenario, ExecutionContext ctx) {
        // 1. Build system prompt
        String systemPrompt = buildSystemPrompt(scenario, ctx);

        // 2. Run agentic loop via ChatClient
        //    ChatClient calls tools automatically; we collect ToolCallRecord per invocation
        //    Loop terminates when LLM returns a final JSON verdict or maxTurns is exceeded

        // 3. Parse final message into TestResult
        //    Expected format: {"passed": true, "verdict": "...", "reasoning": "..."}

        // 4. Return TestResult with accumulated toolCalls list
    }
}
```

**System prompt template (default):**
```
You are an integration test executor. You will be given a test scenario written in natural language.
Your job is to:
1. Identify all actions described (HTTP calls, database queries, service invocations).
2. Execute each action using the available tools.
3. Verify the outcomes match the scenario's expectations.
4. Respond ONLY with a JSON object: {"passed": <bool>, "verdict": "<one line>", "reasoning": "<detail>"}

Base URL for all relative paths: {{baseUrl}}

=== SCENARIO ===
{{rawMarkdown}}
```

### `OrchestratorProperties`
```java
public record OrchestratorProperties(
    int maxTurns,                   // default 10
    String systemPromptTemplate     // overridable; uses {{baseUrl}}, {{rawMarkdown}} placeholders
) {}
```

## Unit Tests

`SpringAiOrchestratorTest` (Mockito, no Spring context):
- `returnsPassedResultWhenLlmReturnsPassJson()` — mock `ChatClient` returns `{"passed":true,...}` → `result.passed() == true`
- `returnsFailedResultWhenLlmReturnsFailJson()` — same, `passed:false`
- `respectsMaxTurnsLimit()` — verify orchestrator stops after `maxTurns` even if LLM doesn't conclude
- `recordsToolCallsWithTiming()` — assert `toolCalls` list populated

`HttpToolTest`:
- Mock `RestClient`, verify headers forwarded, status captured correctly

## Verification

```bash
./gradlew :inquisitor-spring-ai:test
```

## Notes / Open Questions

- Spring AI `@Tool` annotation exact import path — confirm it's `org.springframework.ai.tool.annotation.Tool` in Spring AI 1.x.
- Tool-call interception for `ToolCallRecord` timing: Spring AI may expose a callback/observer; otherwise wrap tool methods manually.
- Should `HttpTool` use `RestClient` (Spring 6+) or `WebClient`? Prefer `RestClient` (blocking, simpler for test scenarios).
- `DatabaseTool.executeSql` should be read-only by default (enforce `Connection.setReadOnly(true)`) — guard against LLM issuing destructive SQL.
- Consider adding a `GraphQlTool` stub (empty, not wired) so users can see the extension point.
