# Spring Boot 4.x Setup (Gradle Kotlin DSL)

## Project Structure (Clean Architecture)

```
src/main/java/com/example/
├── domain/
│   ├── model/          # Aggregates, records, value objects
│   ├── repository/     # Repository interfaces
│   └── service/        # Domain services
├── application/
│   ├── dto/            # Request/Response records
│   ├── mapper/         # Mappers (MapStruct or manual)
│   └── service/        # Application services
├── infrastructure/
│   ├── persistence/    # Spring Data JDBC config
│   ├── config/         # Spring configuration
│   └── security/       # Security setup
└── presentation/
    └── rest/           # REST controllers
```

## `build.gradle.kts` (Library / Microservice)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone")
}

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Data
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Observability — OpenTelemetry (Spring Boot 4 first-class support)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.x")

    // Null safety (JSpecify — Spring Framework 7)
    implementation("org.jspecify:jspecify:1.0.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs += "--enable-preview"
    options.release = 26
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
    useJUnitPlatform()
}
```

## `application.yml`

```yaml
spring:
  application:
    name: example-service

  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/example}
    username: ${DATABASE_USER:example}
    password: ${DATABASE_PASSWORD:example}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 20000

  flyway:
    enabled: true
    locations: classpath:db/migration

  threads:
    virtual:
      enabled: true  # enables virtual threads for Tomcat, @Async, etc.

server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  tracing:
    sampling:
      probability: 1.0   # 1.0 for dev/staging; tune for prod
  otlp:
    tracing:
      endpoint: ${OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

## Main Application Class

```java
@SpringBootApplication
@EnableJdbcAuditing
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
```

## Exception Handling (RFC 7807 ProblemDetail)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList());
        return problem;
    }
}
```

## OpenAPI Configuration

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Example Service API")
                .version("1.0.0"));
    }
}
```

## Flyway Migration Example

```sql
-- src/main/resources/db/migration/V1__create_orders.sql
CREATE TABLE orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    status      VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_item (
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    position     INT NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    quantity     INT NOT NULL,
    price        NUMERIC(10,2) NOT NULL,
    PRIMARY KEY (order_id, position)
);
```

## API Versioning (Spring Framework 7 native)

Choose one strategy and configure it once in `WebMvcConfigurer`. Do not mix strategies.

```java
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        // Option A — media-type parameter (Zalando REST guidelines)
        configurer.useMediaTypeParameterVersioning();   // Accept: application/json;version=2.0

        // Option B — custom header (Microsoft REST guidelines)
        // configurer.useHeaderVersioning("X-API-Version");

        // Option C — query parameter
        // configurer.useQueryParameterVersioning("api-version");
    }
}
```

Then annotate handlers with `version`:
```java
@GetMapping(value = "/{id}", version = "1.0")  public OrderDtoV1 getV1(...) { ... }
@GetMapping(value = "/{id}", version = "2.0")  public OrderDtoV2 getV2(...) { ... }
```

## JSpecify Null Safety

Add `package-info.java` to every package declaring `@NullMarked`. All types within are non-null by default; use `@Nullable` for explicit opt-outs.

```java
// src/main/java/com/example/domain/model/package-info.java
@NullMarked
package com.example.domain.model;

import org.jspecify.annotations.NullMarked;
```

## Virtual Threads (enabled via config)

With `spring.threads.virtual.enabled=true`, Spring Boot 4 automatically uses virtual threads for:
- Tomcat request handling
- `@Async` methods
- Spring MVC `DeferredResult` / `Callable`

Write simple blocking code — the platform handles concurrency. No reactive pipeline required.

## Quick Reference

| Component | Purpose |
|-----------|---------|
| `@SpringBootApplication` | Application entry point |
| `@EnableJdbcAuditing` | Auto-populate `createdAt` / `updatedAt` |
| `spring.threads.virtual.enabled` | Enable virtual threads platform-wide |
| `ProblemDetail` | RFC 7807 error responses |
| `@ConfigurationProperties` | Type-safe externalized config |
| `@ServiceConnection` | TestContainers auto-wiring in tests |
| `@NullMarked` (package-info.java) | Declare package-wide non-null contract (JSpecify) |
| `@Nullable` | Explicit opt-out from `@NullMarked` contract |
| `@ImportHttpServices(Client.class)` | Zero-config HTTP interface client registration |
| `@EnableResilientMethods` | Activate `@Retryable` / `@ConcurrencyLimit` |
| `@Retryable` | Declarative retry with exponential backoff + jitter |
| `@ConcurrencyLimit` | Throttle concurrent method invocations (esp. virtual threads) |
| `@Observed` | Automatic span creation via OpenTelemetry |
| `BeanRegistrar` | AOT-compatible programmatic/conditional bean registration |
| `configureApiVersioning()` | Configure media-type / header / query-param versioning strategy |
| `@GetMapping(version = "2.0")` | Route requests by API version |
