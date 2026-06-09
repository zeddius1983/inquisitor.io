# Design Patterns

---

## Foundational Principle — Zero-Configuration Local Run

> **Every application must start and run fully locally with a single command and no manual infrastructure setup.**

A developer cloning the repository for the first time must be able to do:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :inquisitor-demo:bootRun
```

and have a fully working, connected application — no Postgres installed, no Docker Compose file started manually, no environment variables configured, no README step skipped.

This is not optional polish. It is a hard architectural constraint that shapes how every external dependency is introduced into the project.

### The Rule

**Every external resource must have a `local`-profile substitute that activates automatically:**

| Resource type | Local substitute |
|---|---|
| Relational database (Postgres, MySQL) | TestContainers — `PostgreSQLContainer` |
| Redis / cache | TestContainers — `GenericContainer("redis:7-alpine")` |
| Kafka / message broker | TestContainers — `KafkaContainer` |
| S3 / object storage | TestContainers — `LocalStackContainer` with S3 |
| SMTP / email | TestContainers — `MailHogContainer` or `GenericContainer("mailhog/mailhog")` |
| External HTTP API | WireMock — `WireMockContainer` or in-process `WireMockServer` |
| AI / LLM provider | Spring AI `TestOpenAiApi` or a local Ollama container |
| OAuth2 / identity provider | Spring Security test support or a Keycloak TestContainer |

### How to Implement

Every external resource gets its own autoconfigurable starter module (see Pattern 1). The starter's `local`-profile `@Bean` methods activate the substitute automatically — the consuming application needs no awareness of which substitute is used.

```java
// In inquisitor-redis-starter — DbAutoConfiguration pattern applied to Redis
@Bean
@Profile("local")
@ConditionalOnMissingBean(RedisConnectionDetails.class)
GenericContainer<?> redisContainer() {
    val container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    container.start();
    return container;
}

@Bean
@Profile("local")
@ConditionalOnMissingBean(RedisConnectionDetails.class)
RedisConnectionDetails redisConnectionDetailsLocal(GenericContainer<?> redisContainer) {
    return new ContainerRedisConnectionDetails(redisContainer);
}
```

### What Counts as Acceptable

- **TestContainers** for any service that has a Docker image — preferred because it is the real thing
- **In-memory fakes** (H2 is explicitly excluded — use TestContainers Postgres instead) for stateless services only
- **WireMock** for external HTTP APIs where a Docker image is not available or practical
- **Spring AI local models** (Ollama container) for AI features

### What Is Not Acceptable

- Instructions in a README to "install Postgres locally before running"
- A `.env.example` file that developers must copy and fill in before the app starts
- Hardcoded `localhost:5432` connection strings that only work if the developer happens to have the right service running
- `@MockBean` of a data-layer repository to avoid needing a database

### Checklist for Every New External Dependency

Before merging any PR that adds an external resource:

- [ ] A starter module exists for the resource
- [ ] The starter's `local` profile activates a TestContainers or WireMock substitute automatically
- [ ] `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` starts without error on a clean machine
- [ ] The `unitTest` profile activates the same (or equivalent) substitute for JUnit tests
- [ ] No manual step is documented or required

---

## Pattern 1 — Autoconfigurable Resource Starter

### Intent

Every external resource dependency (database, cache, message broker, AI model, etc.) lives in its own Spring Boot starter module. Adding the module as a Gradle dependency is sufficient to fully configure the resource — no boilerplate in the consuming application. The starter itself handles connection details for every environment through profile-specific autoconfiguration:

| Profile | Behaviour |
|---|---|
| `local` | Starts a Testcontainers instance automatically — zero local setup required |
| `test` | Reuses the same Testcontainers path so integration tests need no manual wiring |
| `prod` / `staging` | Reads connection details from externalized config (env vars, config server) |

### When to Apply

- Any external resource that more than one application module needs to connect to
- Any resource whose local setup is non-trivial (database, broker, vector store, etc.)
- When you want `./gradlew bootRun` to work with zero pre-installed infrastructure

### Module Structure

```
inquisitor-db-starter/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/io/inquisitor/db/
    │   │   ├── DbAutoConfiguration.java    # single class — all environments
    │   │   └── DbProperties.java           # @ConfigurationProperties binding
    │   └── resources/
    │       ├── META-INF/spring/
    │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       ├── application.yml             # shared defaults
    │       ├── application-local.yml
    │       ├── application-unitTest.yml
    │       ├── application-test.yml
    │       └── db/migration/
    │           └── V1__baseline.sql
    └── test/
        └── java/io/inquisitor/db/
            └── DbAutoConfigurationTest.java
```

### `build.gradle.kts`

```kotlin
plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.7"
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Exposed transitively — consuming modules get JdbcClient, CrudRepository, etc. for free
    api("org.springframework.boot:spring-boot-starter-data-jdbc")
    api("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    // Testcontainers on classpath in every environment.
    // @Profile guards on the @Bean methods ensure containers never start in prod/staging.
    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### `DbProperties.java` — Typed Configuration

```java
@ConfigurationProperties(prefix = "inquisitor.db")
public record DbProperties(Connection connection) {

    public record Connection(String url, String username, String password) {}
}
```

Used for profile-specific connection overrides (e.g. `test` environment CI database).
Profile YAML files bind their values here; `local`/`unitTest` profiles skip it entirely
because the container supplies its own coordinates.

### `DbAutoConfiguration.java` — Single Class, All Environments

```java
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(DbProperties.class)
@ConditionalOnClass(DataSource.class)
public class DbAutoConfiguration {

    // ── local & unit-test: start a Testcontainer ────────────────────────────

    @Bean
    @Profile({"local", "unitTest"})
    @ConditionalOnMissingBean
    PostgreSQLContainer<?> postgresContainer() {
        val container = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("inquisitor")
            .withReuse(true);   // keeps the container alive between restarts in local dev
        container.start();
        return container;
    }

    @Bean
    @Profile({"local", "unitTest"})
    @ConditionalOnMissingBean(JdbcConnectionDetails.class)
    JdbcConnectionDetails jdbcConnectionDetailsLocal(PostgreSQLContainer<?> container) {
        return new ContainerJdbcConnectionDetails(container);
    }

    // ── test environment (CI / shared test DB) ───────────────────────────────

    @Bean
    @Profile("test")
    @ConditionalOnMissingBean(JdbcConnectionDetails.class)
    JdbcConnectionDetails jdbcConnectionDetailsTest(DbProperties props) {
        return new PropertiesJdbcConnectionDetails(props.connection());
    }

    // ── prod / staging: no explicit bean ────────────────────────────────────
    // Spring Boot's DataSourceAutoConfiguration reads spring.datasource.* directly.
    // No JdbcConnectionDetails bean → default path applies.

    // ── Private implementations ──────────────────────────────────────────────

    private record ContainerJdbcConnectionDetails(PostgreSQLContainer<?> container)
            implements JdbcConnectionDetails {
        @Override public String getJdbcUrl()        { return container.getJdbcUrl(); }
        @Override public String getUsername()        { return container.getUsername(); }
        @Override public String getPassword()        { return container.getPassword(); }
        @Override public String getDriverClassName() { return container.getDriverClassName(); }
    }

    private record PropertiesJdbcConnectionDetails(DbProperties.Connection conn)
            implements JdbcConnectionDetails {
        @Override public String getJdbcUrl()        { return conn.url(); }
        @Override public String getUsername()        { return conn.username(); }
        @Override public String getPassword()        { return conn.password(); }
        @Override public String getDriverClassName() { return "org.postgresql.Driver"; }
    }
}
```

**Key design decisions:**
- `@AutoConfiguration(before = DataSourceAutoConfiguration.class)` — `JdbcConnectionDetails` must be registered before Spring Boot creates the `DataSource` from it.
- `@ConditionalOnMissingBean` on every `@Bean` — consuming apps can override any individual bean without disabling the whole starter.
- `withReuse(true)` — Testcontainers reuse keeps the container socket alive between `bootRun` restarts; first start is slow, subsequent ones are near-instant.
- Prod/staging produce no `JdbcConnectionDetails` bean — Spring Boot's own autoconfiguration handles `spring.datasource.*` without interference.

### `AutoConfiguration.imports` — Registration

```
# src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
io.inquisitor.db.DbAutoConfiguration
```

One entry. `@Profile` annotations on the `@Bean` methods handle environment selection inside the class.

### Profile-Specific YAML Files

```yaml
# application.yml — shared defaults (all environments)
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 20000
```

```yaml
# application-local.yml — local dev (container URL injected by JdbcConnectionDetails)
logging:
  level:
    org.flywaydb: DEBUG
    org.springframework.jdbc.core: DEBUG
```

```yaml
# application-unitTest.yml — JUnit tests (same container path as local)
spring:
  flyway:
    clean-on-validation-error: true   # reset schema between test runs if migrations drift
```

```yaml
# application-test.yml — CI / shared test environment (real DB)
inquisitor:
  db:
    connection:
      url: ${TEST_DB_URL}
      username: ${TEST_DB_USER}
      password: ${TEST_DB_PASSWORD}
```

```yaml
# application-prod.yml — production (Spring Boot reads spring.datasource.* natively)
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
  flyway:
    baseline-on-migrate: false
```

### Consuming the Starter

```kotlin
// inquisitor-demo/build.gradle.kts — one line, everything else is automatic
dependencies {
    implementation(project(":inquisitor-db-starter"))
}
```

```bash
# Local dev — Postgres container starts automatically
SPRING_PROFILES_ACTIVE=local ./gradlew :inquisitor-demo:bootRun

# Unit tests — unitTest profile spins up a fresh container
SPRING_PROFILES_ACTIVE=unitTest ./gradlew :inquisitor-demo:test

# CI integration tests — test profile connects to the CI database
SPRING_PROFILES_ACTIVE=test ./gradlew :inquisitor-demo:test
```

No `@ServiceConnection`, no `@DynamicPropertySource`, no Docker Compose file needed in the consuming module.

### Overriding in the Consuming Module

```java
// Provide your own JdbcConnectionDetails and the starter's bean is skipped
@Configuration
public class CustomDbConfig {

    @Bean
    public JdbcConnectionDetails jdbcConnectionDetails() {
        // e.g. read from Vault, or point at a PgBouncer proxy
        return new MyVaultJdbcConnectionDetails();
    }
}
```

### Flyway Migrations: Starter-Owned vs App-Owned

| Scenario | Where migrations live |
|---|---|
| Starter owns the full schema (DB-per-service) | `inquisitor-db-starter/src/main/resources/db/migration/` |
| App owns the schema (shared DB or per-module schema) | `inquisitor-demo/src/main/resources/db/migration/` |
| Both contribute migrations | Separate locations: `classpath:db/migration,classpath:db/app-migration` in `application.yml` |

---

## Pattern 2 — Sealed Interface as Algebraic Data Type (ADT)

Model domain states that have a fixed, known set of variants using sealed interfaces + records. This is Java's closest approximation to Scala's `sealed trait` + `case class`.

```java
// Domain result type — exhaustive, no nulls, no boolean flags
sealed interface OrderResult {
    record Accepted(Order order)               implements OrderResult {}
    record Rejected(String reason)             implements OrderResult {}
    record Duplicate(UUID existingOrderId)     implements OrderResult {}
}

// Exhaustive switch — compiler enforces all cases are handled
String message = switch (result) {
    case OrderResult.Accepted a  -> "Order " + a.order().id() + " accepted";
    case OrderResult.Rejected r  -> "Rejected: " + r.reason();
    case OrderResult.Duplicate d -> "Duplicate of " + d.existingOrderId();
};

// Usage in service
public OrderResult placeOrder(CreateOrderRequest req) {
    if (isDuplicate(req)) return new OrderResult.Duplicate(findExisting(req).id());
    if (!isValid(req))    return new OrderResult.Rejected("Invalid product code");
    return new OrderResult.Accepted(orderRepository.save(Order.create(req)));
}
```

**Why over exceptions:** exceptions break functional chains and obscure expected failure modes; a sealed result type makes every outcome visible in the method signature.

---

## Pattern 3 — Anti-Corruption Layer (ACL)

Wrap external APIs or legacy systems behind an internal interface so domain code never imports external types directly.

```java
// Internal port — defined in domain, no external imports
public interface WeatherPort {
    Optional<WeatherCondition> currentConditions(String city);
}

public record WeatherCondition(String city, double tempCelsius, String summary) {}

// Adapter — lives in infrastructure layer, imports the external client
@Component
@RequiredArgsConstructor
class OpenWeatherAdapter implements WeatherPort {

    private final OpenWeatherClient client;

    @Override
    public Optional<WeatherCondition> currentConditions(String city) {
        return client.fetchCurrent(city)                     // returns external DTO
            .map(dto -> new WeatherCondition(               // translate at the boundary
                dto.cityName(),
                dto.main().temp() - 273.15,
                dto.weather().get(0).description()
            ));
    }
}
```

**Rule:** the domain layer depends on `WeatherPort`; only the adapter depends on `OpenWeatherClient`. Swapping the provider requires changing only the adapter class.

---

## Pattern Catalogue (Summary)

| Pattern | Reference section | Use when |
|---|---|---|
| Autoconfigurable Resource Starter | Pattern 1 | Any external resource (DB, cache, broker, AI) needed in ≥1 module |
| Sealed ADT | Pattern 2 | Modelling domain states with known variants; replacing boolean flags or exception-as-control-flow |
| Anti-Corruption Layer | Pattern 3 | Consuming external APIs or legacy services |
