# Task 07 — JUnit 5 Extension

## Goal

Implement `@InquisitorTest` and its backing JUnit 5 extension so that scenario `.md` files are auto-discovered and each runs as a named test case inside a standard `@SpringBootTest` class. Also provide `ScenarioExecutor` parameter injection for manual single-scenario tests.

## Module placement

Implemented in `inquisitor-autoconfigure` (avoids a new module; JUnit 5 is `testImplementation` in consumers anyway). If the extension grows significantly it can be extracted to `inquisitor-test` in a later task.

## New dependencies in `inquisitor-autoconfigure/build.gradle.kts`

```kotlin
compileOnly("org.junit.jupiter:junit-jupiter-api")        // compile-only; consumers bring JUnit 5
compileOnly("org.springframework.boot:spring-boot-test")  // compile-only
```

## Package Layout

```
io.inquisitor.autoconfigure.testing
├── InquisitorTest.java            @Target(TYPE) annotation
├── InquisitorExtension.java       implements several JUnit 5 Extension interfaces
└── ScenarioInvocationContext.java  TestTemplateInvocationContext impl (inner or package-private)
```

## `@InquisitorTest` Annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(InquisitorExtension.class)
public @interface InquisitorTest {
    String[] scenarioDirs() default {"classpath:scenarios/"};
}
```

## `InquisitorExtension`

Implements:
- `BeforeAllCallback` — resolves `ApplicationContext`, discovers `.md` files, parses each into a `Scenario`, stores list on `ExtensionContext.Store`
- `TestTemplateInvocationContextProvider` — returns one `TestTemplateInvocationContext` per `Scenario`; each context supplies a display name (= `scenario.name()`) and a `ParameterResolver` for `ScenarioExecutor` and `Scenario`
- `ParameterResolver` — for `@Test` methods that declare `ScenarioExecutor executor` or `Scenario scenario` parameters (non-template usage)

### Discovery logic

```java
private List<Scenario> discoverScenarios(String[] dirs, ApplicationContext ctx) {
    ScenarioParser parser = ctx.getBean(ScenarioParser.class);
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    List<Scenario> scenarios = new ArrayList<>();
    for (String dir : dirs) {
        Resource[] resources = resolver.getResources(dir + "**/*.md");
        for (Resource r : resources) {
            String content = r.getContentAsString(StandardCharsets.UTF_8);
            Scenario s = parser.parse(content);
            // if name is blank, use filename without extension as fallback
            scenarios.add(s.name().isBlank()
                ? new Scenario(stripExtension(r.getFilename()), s.description(), s.rawMarkdown())
                : s);
        }
    }
    return scenarios;
}
```

### Template invocation

Each invocation:
1. Retrieves `Scenario` from context store by index
2. Resolves `ExecutionContext` from `InquisitorProperties` (base URL, etc.)
3. Calls `scenarioExecutor.execute(scenario, executionContext)`
4. Asserts `result.passed()`, attaching `result.reasoning()` to the failure message via `Assertions.fail()`

### `@Test` parameter injection (non-template mode)

When a user writes:
```java
@Test
void myCustomTest(ScenarioExecutor executor, Scenario scenario) { ... }
```
`ParameterResolver` injects the beans from the `ApplicationContext`.

## Unit Tests

`InquisitorExtensionTest`:
- Uses `@SpringBootTest` with a minimal `@TestConfiguration` providing mock `ScenarioParser` + `ScenarioExecutor` beans
- Verifies that 3 `.md` files in `src/test/resources/ext-scenarios/` produce 3 test invocations
- Verifies display name matches scenario `name`
- Verifies a `passed=false` result causes a test failure with `reasoning` in the message

## Verification

```bash
./gradlew :inquisitor-demo:test --tests "*.ScenarioSuiteTest"
# Gradle test report shows one test entry per .md file, named by H1 heading
```

Inspect `build/reports/tests/test/index.html` in `inquisitor-demo` — each scenario appears as a separate test case under `ScenarioSuiteTest`.

## Notes / Open Questions

- `TestTemplateInvocationContextProvider` requires the test method to be annotated `@TestTemplate`, not `@Test`. `InquisitorTest`-annotated classes need a `@TestTemplate`-annotated method — or the extension must synthesize one. Alternative: use `DynamicTest` via `@TestFactory`. **Decision needed:** `@TestTemplate` (extension-driven, closer to JUnit lifecycle) vs `@TestFactory` (simpler, user writes a `Stream<DynamicTest>` factory method). Lean toward `@TestTemplate` for the "zero boilerplate in the test class" experience.
- `PathMatchingResourcePatternResolver` is from `spring-core` — already on classpath via autoconfigure module deps.
- Thread-safety: `ExtensionContext.Store` is per-context (per-class for `BeforeAllCallback`) — safe for parallel test execution.
- Should failed scenarios attach the full `ToolCallRecord` list to the report? JUnit 5 `ReportEntry` API could be used for structured output.
