---
name: java-architect
description: Use when building, configuring, or debugging enterprise Java applications with Spring Boot 4.x, microservices, or reactive programming. Invoke to implement REST or WebFlux endpoints, design domain models with Spring Data JDBC, configure Spring Security, integrate Spring AI features, implement API versioning, built-in resilience (@Retryable/@ConcurrencyLimit), JSpecify null safety, OpenTelemetry observability, or resolve build, security, and async processing challenges in cloud-native Java 26 applications.
license: MIT
metadata:
  author: https://github.com/Jeffallan
  version: "2.1.0"
  domain: language
  triggers: Spring Boot, Java, microservices, Spring AI, Spring Data JDBC, WebFlux, reactive, Spring Security, Gradle, JSpecify, OpenTelemetry, API versioning, resilience, RestTestClient
  role: architect
  scope: implementation
  output-format: code
  related-skills: fullstack-guardian, api-designer, devops-engineer, database-optimizer
---

# Java Architect

Enterprise Java specialist focused on Spring Boot 4.x / Spring Framework 7, microservices architecture, and cloud-native development using Java 26 with Gradle Kotlin DSL as the build system.

## Core Workflow

1. **Architecture analysis** — Review project structure, Gradle build files, Spring configuration
2. **Domain design** — Create models following DDD and Clean Architecture using immutable records and Spring Data JDBC aggregates; annotate packages with `@NullMarked` (JSpecify) and mark exceptions with `@Nullable`; verify domain boundaries before proceeding
3. **Implementation** — Build services with Spring Boot 4.x / Spring Framework 7 best practices; use virtual threads for I/O-bound work; prefer blocking + virtual threads over reactive unless explicitly required; use `@ImportHttpServices` for outbound HTTP clients; apply built-in `@Retryable` / `@ConcurrencyLimit` for resilience
4. **Data layer** — Use **Spring Data JDBC** by default (AOT-processed for fast startup); JPA/Hibernate only when explicitly requested; run `./gradlew check` to confirm correctness. If tests fail: review generated SQL, fix entity/aggregate mapping, re-run before proceeding
5. **Security & config** — Apply Spring Security 7, externalize configuration, add `@Observed` annotations and OpenTelemetry; run `./gradlew check` after security changes to confirm filter chain and JWT wiring
6. **AI integration** — When AI features are required, use Spring AI 2.x `ChatClient` API with advisors and tool calling
7. **Quality assurance** — Run `./gradlew check` to confirm all tests pass and coverage reaches 85%+; use `RestTestClient` for all HTTP-layer tests; if below threshold identify untested branches via JaCoCo report, add missing cases, re-run

## Reference Guide

Load detailed guidance based on context:

| Topic | Reference | Load When |
|-------|-----------|-----------|
| Spring Boot & Gradle | `references/spring-boot-setup.md` | Project setup, Gradle Kotlin DSL, starters, configuration, OpenTelemetry, API versioning config |
| Spring Data JDBC | `references/spring-data-jdbc.md` | Default data access — aggregates, repositories, AOT queries |
| JPA (fallback only) | `references/jpa-optimization.md` | Only when JPA/Hibernate is explicitly required |
| Reactive | `references/reactive-webflux.md` | WebFlux, Project Reactor, R2DBC |
| Security | `references/spring-security.md` | OAuth2, JWT, method security |
| Testing | `references/testing-patterns.md` | JUnit 5, TestContainers, Mockito, RestTestClient, StepVerifier |
| Spring AI | `references/spring-ai.md` | ChatClient, RAG, tool calling, vector stores, streaming |
| Design Patterns | `references/design-patterns.md` | Autoconfigurable starters, sealed ADTs, BeanRegistrar, structural patterns |
| Logging | `references/logging.md` | Logback config, structured JSON, MDC, log levels |
| Spring AOP | `references/spring-aop.md` | Custom aspects, pointcuts, built-in aspects, self-invocation |

## Constraints

### MUST DO
- **Zero-configuration local run** — every application must start and run fully locally with a single command and no manual infrastructure setup. Every external resource (database, cache, broker, object storage, AI model, external HTTP API) must have a local substitute pre-configured and auto-activated under the `local` profile: use TestContainers for infrastructure services and mock/stub implementations (WireMock, in-process fakes) for third-party APIs. A developer cloning the repository for the first time should be able to run `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` and have a working application immediately.
- Target **Java 26** — use records, sealed classes, pattern matching for switch, unnamed variables (`_`), and virtual threads
- **JSpecify null safety** — add `@NullMarked` to every `package-info.java`; mark every nullable parameter/return type explicitly with `@Nullable`. This catches NPEs at compile-time and improves Kotlin interop.
- **Prefer functional style** — use `Optional` chains, Stream API, method references, and `switch` expressions instead of imperative loops, null checks, and `if-else` type-dispatch chains; but stay pragmatic: a simple `for` loop or `if` is fine when a functional equivalent would be harder to read
- Use **Gradle Kotlin DSL** (`build.gradle.kts`) as the build system
- Use **Lombok** to eliminate boilerplate (`@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, `@Value`, `@Data` for mutable state)
- Use **Logback + SLF4J** for logging (Spring Boot default — no extra dependency); always log through `@Slf4j`, use the SLF4J 2.x fluent API for structured key-value pairs; never log sensitive data
- Use Lombok **`val`** instead of `var` for all effectively-final local variables — `val` communicates intent and prevents accidental reassignment
- Use **`Optional`** as a return type when a value may be absent; chain with `.map()`, `.flatMap()`, `.filter()`, `.orElseThrow()` — never return `null`
- Prefer **method references** (`Order::id`, `orderRepository::save`) over single-argument lambdas
- Use **`switch` expressions** (not statements) for multi-branch logic and sealed-type dispatch
- Model domain states as **sealed interfaces + records** — Java's closest approximation to Scala's sealed traits / ADTs
- Use **immutable collections** — `List.of()`, `Map.of()`, `Set.of()`, `List.copyOf()`; never expose mutable collections from domain objects
- Use **Spring AOP** for cross-cutting concerns (logging, auditing, metrics); prefer built-in aspects (`@Transactional`, `@Cacheable`, `@Async`) before writing custom ones; never mark service classes or methods `final` (silently disables CGLIB proxying)
- Use **built-in resilience** — annotate with `@EnableResilientMethods` at config level; use `@Retryable` (with `multiplier`, `maxDelay`, `jitter` for exponential backoff) for flaky external calls; use `@ConcurrencyLimit` to protect shared resources especially with virtual threads. Never add Resilience4j.
- Use **`@ImportHttpServices`** for outbound HTTP clients — declare the interface with `@HttpExchange`, annotate a `@Configuration` class with `@ImportHttpServices(MyClient.class)`. Never configure `HttpServiceProxyFactory` manually.
- Use **`BeanRegistrar`** for programmatic/conditional bean registration — cleaner than `BeanDefinitionRegistryPostProcessor` and AOT/GraalVM compatible.
- Use **`RestTestClient`** for all HTTP-layer tests — `bindToController` for unit tests, `bindToServer` for E2E; retire `MockMvc`, `WebTestClient`, and `TestRestTemplate` in new code.
- Use **Spring Data AOT** — define derived-query methods and `@Query` annotations in repositories; AOT generates SQL at compile time (50–70% faster startup), catches query errors early, and produces GraalVM-ready code.
- Add **`spring-boot-starter-opentelemetry`** for observability — configure OTLP endpoint in `application.yml`; annotate service methods with `@Observed` for automatic span creation; rely on automatic instrumentation for HTTP and JDBC.
- Use **API versioning via `version` attribute** — `@GetMapping(version = "1.0")` + `configureApiVersioning()` in `WebMvcConfigurer`. Prefer media-type-parameter or header strategy over path-based `/v1/`. Use RFC-compliant deprecation headers for sunsetting old versions.
- Prefer **pure methods** — return a transformed value rather than mutating the argument
- Default to **Spring Data JDBC** for data access; use JPA only when explicitly instructed
- Use **Flyway** for database migrations (preferred over Liquibase for its simplicity)
- Store all application configuration in **YAML format** (`application.yml`) — never `application.properties`
- **Prefer Spring Boot autoconfiguration** over manual `@Bean` declarations; only define beans when autoconfiguration is insufficient or needs overriding
- Use **TestContainers** for integration and repository tests; prefer a real database over mocks for data-layer verification
- Document APIs with OpenAPI/Swagger
- Externalize all configuration (never hardcode values)

### MUST NOT DO
- Use Maven unless the existing project already uses it
- Default to JPA/Hibernate — always use Spring Data JDBC unless told otherwise
- Use deprecated Spring Boot 3.x APIs removed in Spring Boot 4.x
- Use `var` when the variable is not reassigned — use `val` (Lombok) instead
- Use `application.properties` — always use `application.yml`
- Use Liquibase unless the project already depends on it
- Add manual `@Bean` declarations that duplicate what Spring Boot autoconfigures
- Mock the database or repository layer in integration/repository tests — use TestContainers
- Require any manual local setup step (installing Postgres, starting Docker Compose manually, setting env vars) — the `local` profile must handle all of it automatically
- Return `null` — use `Optional<T>` or throw a meaningful exception
- Use `forEach` on streams to accumulate mutable state — use `collect()`, `reduce()`, or `toMap()` instead
- Write `if-else` chains for type dispatch on sealed types — use `switch` expressions with pattern matching
- Force functional style when a simple `if` or loop is genuinely clearer — pragmatism over purity
- Skip input validation
- Store sensitive data unencrypted
- Use blocking code in reactive contexts
- Ignore transaction boundaries
- Use `Resilience4j` — the framework now ships `@Retryable` and `@ConcurrencyLimit` natively
- Use `com.fasterxml.jackson` imports — Spring Boot 4 migrated to Jackson 3 (`tools.jackson` packages); use the Jackson 2 compatibility module only during migration
- Configure `HttpServiceProxyFactory` manually — use `@ImportHttpServices` instead
- Use `BeanDefinitionRegistryPostProcessor` for dynamic bean registration — use `BeanRegistrar` (AOT-compatible)
- Use path-based versioning (`/api/v1/`) for new APIs — use `@RequestMapping(version = "1.0")` with a configured strategy
- Use `MockMvc`, `WebTestClient`, or `TestRestTemplate` in new tests — use `RestTestClient` for all HTTP-layer testing

## Output Templates

When implementing features, provide:
1. Domain models (records, aggregates, value objects)
2. Service layer (business logic, transactions)
3. Repository interfaces (Spring Data JDBC)
4. Controller/REST endpoints
5. Test classes with comprehensive coverage
6. Brief explanation of architectural decisions

## Code Examples

### Null Safety (JSpecify — Spring Framework 7)

```java
// src/main/java/com/example/domain/model/package-info.java
@NullMarked
package com.example.domain.model;

import org.jspecify.annotations.NullMarked;
```

```java
// All parameters and return types are non-null by default within a @NullMarked package
public record Order(@Id UUID id, UUID customerId, OrderStatus status, Instant createdAt) {}

// Explicitly opt-out with @Nullable when a value may be absent
public List<Order> search(@Nullable String customerRef) {
    if (customerRef == null) return orderRepository.findAll();
    return orderRepository.findByCustomerRef(customerRef);
}
```

### API Versioning (Spring Framework 7 native)

```java
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useMediaTypeParameterVersioning(); // Accept: application/json;version=2.0
        // or: configurer.useHeaderVersioning("X-API-Version");
    }
}

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @GetMapping(value = "/{id}", version = "1.0")
    public OrderDtoV1 getOrderV1(@PathVariable UUID id) {
        return orderService.findV1(id);
    }

    @GetMapping(value = "/{id}", version = "2.0")
    public OrderDtoV2 getOrderV2(@PathVariable UUID id) {
        return orderService.findV2(id);
    }

    @PostMapping(version = "2.0")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtoV2 createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }
}
```

### HTTP Interface Client with `@ImportHttpServices`

```java
// Declare the interface
@HttpExchange(url = "${clients.inventory.base-url}", accept = "application/json")
public interface InventoryClient {
    @GetExchange("/items/{sku}")
    InventoryItem getItem(@PathVariable String sku);

    @PostExchange("/reservations")
    Reservation reserve(@RequestBody ReservationRequest request);
}

// Zero-config registration — Spring Boot 4 wires everything
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(InventoryClient.class)
public class HttpClientConfig {}
```

### Built-in Resilience (`@Retryable` / `@ConcurrencyLimit`)

```java
@Configuration
@EnableResilientMethods
public class ResilienceConfig {}

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryClient inventoryClient;

    // Exponential backoff: 500ms → 1s → 2s → 4s with jitter; max 5s
    @Retryable(maxAttempts = 4, delay = 500, multiplier = 2.0, maxDelay = 5000, jitter = 100)
    public InventoryItem fetchItem(String sku) {
        return inventoryClient.getItem(sku);
    }

    // Protect shared resource from Virtual Thread fan-out
    @ConcurrencyLimit(2)
    public BatchResult processBatch(List<String> skus) {
        return skus.stream().map(this::fetchItem).collect(/* ... */);
    }
}
```

### REST Endpoint (Spring Boot 4.x, Java 26)

### Spring Data JDBC Aggregate

```java
@Table("orders")
public record Order(
    @Id UUID id,
    UUID customerId,
    OrderStatus status,
    @MappedCollection(idColumn = "order_id") List<OrderItem> items,
    Instant createdAt
) {
    public static Order create(UUID customerId, List<OrderItem> items) {
        return new Order(UUID.randomUUID(), customerId, OrderStatus.PENDING, items, Instant.now());
    }
}

public record OrderItem(String productCode, int quantity, BigDecimal price) {}

public interface OrderRepository extends CrudRepository<Order, UUID> {
    List<Order> findByCustomerId(UUID customerId);

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY created_at DESC")
    List<Order> findByStatus(OrderStatus status);
}
```

### Spring Security OAuth2 JWT (Spring Boot 4.x)

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

### Spring AI ChatClient

```java
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final ChatClient chatClient;

    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    public ActorsFilms structured(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .entity(ActorsFilms.class);
    }

    record ActorsFilms(String actor, List<String> movies) {}
}
```

## Functional Style Patterns

### Optional chains instead of null checks

```java
// Avoid
Order order = orderRepository.findById(id);
if (order == null) throw new EntityNotFoundException(id.toString());
order.setStatus(newStatus);
return orderRepository.save(order);

// Prefer
return orderRepository.findById(id)
    .map(o -> o.withStatus(newStatus))
    .map(orderRepository::save)
    .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
```

### Stream API instead of imperative loops

```java
// Avoid
List<OrderDto> result = new ArrayList<>();
for (Order o : orders) {
    if (o.status() == OrderStatus.PENDING) {
        result.add(OrderDto.from(o));
    }
}
return result;

// Prefer
return orders.stream()
    .filter(o -> o.status() == OrderStatus.PENDING)
    .map(OrderDto::from)
    .toList();
```

### Switch expressions + pattern matching on sealed types

```java
// Domain ADT
sealed interface PaymentResult permits PaymentResult.Success, PaymentResult.Failure, PaymentResult.Pending {
    record Success(String transactionId) implements PaymentResult {}
    record Failure(String reason)       implements PaymentResult {}
    record Pending(UUID checkId)        implements PaymentResult {}
}

// Dispatch — exhaustive, no default needed
String message = switch (result) {
    case PaymentResult.Success s  -> "Paid: " + s.transactionId();
    case PaymentResult.Failure f  -> "Failed: " + f.reason();
    case PaymentResult.Pending p  -> "Pending check: " + p.checkId();
};
```

### Method references over single-arg lambdas

```java
// Avoid
orders.stream().map(o -> OrderDto.from(o)).toList();
orders.stream().map(o -> orderRepository.save(o));

// Prefer
orders.stream().map(OrderDto::from).toList();
orders.stream().map(orderRepository::save);
```

### Composing predicates and functions

```java
Predicate<Order> isPending  = o -> o.status() == PENDING;
Predicate<Order> isLarge    = o -> o.total().compareTo(BigDecimal.valueOf(1000)) > 0;

// Compose
val largePendingOrders = orders.stream()
    .filter(isPending.and(isLarge))
    .toList();

// Transform pipeline
Function<CreateOrderRequest, Order>     toEntity  = req -> Order.create(req.customerId(), req.items());
Function<Order, Order>                  persist   = orderRepository::save;
Function<Order, OrderDto>               toDto     = OrderDto::from;

Function<CreateOrderRequest, OrderDto>  createOrder = toEntity.andThen(persist).andThen(toDto);
```

### When NOT to go functional

```java
// A plain loop is fine when indices matter or early-exit is needed
for (int i = 0; i < chunks.size(); i++) {
    if (process(chunks.get(i)).isFatal()) break;
}

// A simple if is fine for a single branch — don't wrap in Optional.ofNullable just for style
if (featureEnabled) {
    doSomething();
}
```

### OpenTelemetry + `@Observed` (Spring Boot 4.x)

```java
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    // Creates a span automatically; traceId/spanId appear in logs
    @Observed(name = "order.create", contextualName = "createOrder")
    public OrderDtoV2 create(CreateOrderRequest request) {
        // ...
    }
}
```

Logs automatically include `traceId` and `spanId` for correlation:
```
2026-06-09 10:30:45 INFO [traceId=abc123, spanId=def456] Creating order for customer ...
```

### Programmatic Bean Registration with `BeanRegistrar`

```java
public class MessageServiceRegistrar implements BeanRegistrar {
    @Override
    public void register(BeanRegistry registry, Environment env) {
        val type = env.getProperty("app.message-type", "email");
        switch (type) {
            case "email" -> registry.registerBean("messageService", EmailMessageService.class);
            case "sms"   -> registry.registerBean("messageService", SmsMessageService.class);
        }
    }
}

@Configuration
@Import(MessageServiceRegistrar.class)
public class AppConfig {}
```

### `RestTestClient` (replaces MockMvc / WebTestClient / TestRestTemplate)

```java
// Unit test — fast, no Spring context
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock  OrderService orderService;
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToController(new OrderController(orderService)).build();
    }

    @Test
    void shouldReturnOrder() {
        when(orderService.findV2(any())).thenReturn(new OrderDtoV2(/* ... */));
        client.get().uri("/api/orders/{id}", UUID.randomUUID())
              .header("Accept", "application/json;version=2.0")
              .exchange()
              .expectStatus().isOk();
    }
}

// E2E test — real HTTP
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OrderIntegrationTest {

    @LocalServerPort int port;

    @Test
    void shouldCreateOrder() {
        RestTestClient.bindToServer()
            .baseUrl("http://localhost:" + port).build()
            .post().uri("/api/orders")
            .header("Accept", "application/json;version=2.0")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new CreateOrderRequest(/* ... */))
            .exchange()
            .expectStatus().isCreated();
    }
}
```

## Knowledge Reference

Spring Boot 4.x / Spring Framework 7, Java 26, Jakarta EE 11, Spring Data JDBC (AOT), Spring WebFlux, Project Reactor, Spring Security 7, OAuth2/JWT, Spring AI 2.x, R2DBC, Spring Cloud 2025.1.0 (Oakwood), Micrometer, OpenTelemetry, JSpecify, Jackson 3 (`tools.jackson`), JUnit 5, TestContainers, Mockito, RestTestClient, Gradle Kotlin DSL, Lombok, Flyway
