# Task 04 — `inquisitor-autoconfigure` Module

## Goal

Implement the Spring Boot 4 auto-configuration that wires all Inquisitor beans into a user's application context automatically, following the standard Spring Boot autoconfigure pattern.

## Dependencies

- `project(":inquisitor-spring-ai")`
- `spring-boot-autoconfigure` (via BOM)
- `spring-boot-configuration-processor` (optional, annotationProcessor scope — generates metadata)
- `junit-jupiter` + `spring-boot-test` + `spring-boot-starter-test` (test scope)

## Package Layout

```
io.inquisitor.autoconfigure
├── InquisitorProperties.java          @ConfigurationProperties("inquisitor")
└── InquisitorAutoConfiguration.java   @AutoConfiguration

src/main/resources/
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Types

### `InquisitorProperties`
```java
@ConfigurationProperties(prefix = "inquisitor")
public record InquisitorProperties(
    String baseUrl,                            // required for HttpTool activation
    @DefaultValue("10") int maxTurns,
    @DefaultValue("classpath:scenarios/") List<String> scenarioDirs,
    String model                               // optional; overrides Spring AI model selection
) {}
```

### `InquisitorAutoConfiguration`
```java
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(InquisitorProperties.class)
public class InquisitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ScenarioParser scenarioParser() {
        return new DefaultScenarioParser();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "inquisitor", name = "base-url")
    public HttpTool httpTool(InquisitorProperties props) {
        return new HttpTool(props.baseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public DatabaseTool databaseTool(DataSource dataSource) {
        return new DatabaseTool(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringAiOrchestrator scenarioExecutor(
        ChatClient.Builder chatClientBuilder,
        List<Object> inquisitorTools,          // collected HttpTool + DatabaseTool if present
        InquisitorProperties props
    ) { ... }
}
```

### `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
io.inquisitor.autoconfigure.InquisitorAutoConfiguration
```

### `additional-spring-configuration-metadata.json`
IDE completion hints for `inquisitor.base-url`, `inquisitor.max-turns`, `inquisitor.scenario-dirs`, `inquisitor.model`.

## Tests

`InquisitorAutoConfigurationTest` using `ApplicationContextRunner`:
```java
new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(InquisitorAutoConfiguration.class))
    .withBean(ChatClient.Builder.class, ...)
    .withPropertyValues("inquisitor.base-url=http://localhost:8080")
    .run(ctx -> {
        assertThat(ctx).hasSingleBean(ScenarioParser.class);
        assertThat(ctx).hasSingleBean(HttpTool.class);
        assertThat(ctx).hasSingleBean(ScenarioExecutor.class);
    });
```

Additional slices:
- `doesNotRegisterHttpToolWhenBaseUrlMissing()`
- `doesNotRegisterDatabaseToolWhenNoDataSource()`
- `userBeanTakesPrecedenceOverAutoConfigured()` — `@ConditionalOnMissingBean` respected

## Verification

```bash
./gradlew :inquisitor-autoconfigure:test
```

## Notes / Open Questions

- Spring Boot 4 uses `@AutoConfiguration` (not `@Configuration` + `@EnableAutoConfiguration`). Confirm annotation is in `org.springframework.boot.autoconfigure`.
- `@DefaultValue` on record components — verify this is supported by Spring Boot 4's `@ConfigurationProperties` binding for records.
- Collecting `inquisitorTools` as `List<Object>` may be fragile. Alternative: introduce an `InquisitorTool` marker interface that all built-in tools implement, and inject `List<InquisitorTool>`.
- Should `InquisitorAutoConfiguration` carry `@AutoConfigureAfter(SpringAiAutoConfiguration.class)` to ensure `ChatClient.Builder` is available?
