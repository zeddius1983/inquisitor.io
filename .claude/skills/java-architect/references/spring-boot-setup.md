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

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.x")

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
