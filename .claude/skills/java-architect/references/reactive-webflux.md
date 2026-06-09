# Reactive WebFlux (Spring Boot 4.x)

> Use reactive only when throughput or streaming requirements justify it. For standard CRUD microservices, prefer virtual threads + blocking code (`spring.threads.virtual.enabled=true`).

## WebFlux Controller

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Flux<UserResponse> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> getById(@PathVariable UUID id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public Mono<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return userService.delete(id);
    }

    // Server-sent events for streaming
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserResponse> stream() {
        return userService.findAll().delayElements(Duration.ofMillis(100));
    }
}
```

## Reactive Service Layer

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Flux<UserResponse> findAll() {
        return userRepository.findAll().map(UserResponse::from);
    }

    public Mono<UserResponse> findById(UUID id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .switchIfEmpty(Mono.error(new EntityNotFoundException("User not found: " + id)));
    }

    public Mono<UserResponse> create(UserRequest request) {
        return Mono.just(User.create(request.email(), request.username()))
            .flatMap(userRepository::save)
            .map(UserResponse::from);
    }

    public Mono<UserResponse> update(UUID id, UserRequest request) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new EntityNotFoundException("User not found: " + id)))
            .map(u -> u.withEmail(request.email()).withUsername(request.username()))
            .flatMap(userRepository::save)
            .map(UserResponse::from);
    }

    public Mono<Void> delete(UUID id) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new EntityNotFoundException("User not found: " + id)))
            .flatMap(userRepository::delete);
    }
}
```

## R2DBC Entity and Repository

```java
@Table("users")
public record User(
    @Id UUID id,
    String email,
    String username,
    Boolean active,
    @CreatedDate Instant createdAt,
    @LastModifiedDate Instant updatedAt
) {
    public static User create(String email, String username) {
        return new User(UUID.randomUUID(), email, username, true, null, null);
    }

    public User withEmail(String email) {
        return new User(id, email, username, active, createdAt, updatedAt);
    }

    public User withUsername(String username) {
        return new User(id, email, username, active, createdAt, updatedAt);
    }
}

public interface UserRepository extends ReactiveCrudRepository<User, UUID> {

    Mono<User> findByEmail(String email);

    Flux<User> findByActiveTrue();

    @Query("SELECT * FROM users WHERE email LIKE CONCAT('%', :domain) ORDER BY created_at DESC")
    Flux<User> findByEmailDomain(String domain);
}
```

## R2DBC Configuration

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/example
    username: ${DATABASE_USER:example}
    password: ${DATABASE_PASSWORD:example}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
  data:
    r2dbc:
      repositories:
        enabled: true
```

## `build.gradle.kts` (WebFlux / R2DBC)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.flywaydb:flyway-core")        // migrations still use JDBC
    runtimeOnly("org.postgresql:postgresql")           // Flyway needs JDBC driver

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:r2dbc")
    testImplementation("org.testcontainers:postgresql")
}
```

## WebClient for External APIs

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient externalApiClient(WebClient.Builder builder) {
        return builder
            .baseUrl("${external.api.url}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}

@Component
@RequiredArgsConstructor
public class ExternalApiClient {

    private final WebClient externalApiClient;

    public Mono<ExternalDto> fetchById(UUID id) {
        return externalApiClient.get()
            .uri("/resources/{id}", id)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                r -> r.bodyToMono(String.class).map(ResourceNotFoundException::new))
            .bodyToMono(ExternalDto.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)))
            .timeout(Duration.ofSeconds(5));
    }
}
```

## Reactor Operators Quick Reference

```java
// Transform synchronously
Mono<String> upper = Mono.just("hello").map(String::toUpperCase);

// Chain async operations
Mono<OrderDetails> details = orderRepository.findById(id)
    .flatMap(order -> itemRepository.findByOrderId(order.id())
        .collectList()
        .map(items -> new OrderDetails(order, items)));

// Combine independent sources
Mono<UserProfile> profile = Mono.zip(
    userRepository.findById(userId),
    addressRepository.findByUserId(userId),
    (user, address) -> new UserProfile(user, address)
);

// Error fallback
Mono<User> safe = userRepository.findById(id)
    .onErrorResume(DataAccessException.class, e -> cacheService.findUser(id))
    .doOnError(e -> log.error("Failed to fetch user {}", id, e));

// Backpressure / batching
Flux<Result> processed = dataRepository.findAll()
    .buffer(100)
    .flatMap(batch -> processBatch(batch), 4); // max 4 concurrent batches
```

## Testing Reactive Code

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    @Test
    void shouldReturnUser() {
        val user = User.create("alice@example.com", "alice");
        when(userRepository.findById(user.id())).thenReturn(Mono.just(user));

        StepVerifier.create(userService.findById(user.id()))
            .assertNext(r -> assertThat(r.email()).isEqualTo("alice@example.com"))
            .verifyComplete();
    }

    @Test
    void shouldErrorWhenNotFound() {
        when(userRepository.findById(any())).thenReturn(Mono.empty());

        StepVerifier.create(userService.findById(UUID.randomUUID()))
            .expectError(EntityNotFoundException.class)
            .verify();
    }
}
```

## Quick Reference

| Operator | Purpose |
|---|---|
| `.map()` | Synchronous 1-to-1 transform |
| `.flatMap()` | Async transform (returns Mono/Flux) |
| `.switchIfEmpty()` | Fallback when source is empty |
| `.filter()` | Keep elements matching predicate |
| `.zip()` | Combine multiple Monos |
| `.buffer(n)` | Collect n elements into List |
| `.collectList()` | Flux → Mono<List> |
| `.onErrorResume()` | Recover from error |
| `.retryWhen()` | Retry with backoff |
| `.timeout()` | Cancel if no element within duration |
| `StepVerifier` | Test reactive pipelines |
