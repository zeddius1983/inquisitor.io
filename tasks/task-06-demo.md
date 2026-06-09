# Task 06 — `inquisitor-demo` Module

## Goal

Implement a realistic Spring Boot 4 REST application that serves as the end-to-end validation target for Inquisitor. The demo app exposes a small but meaningful API (Users + Orders), and its test suite runs the scenario files through the full LLM orchestrator loop.

## Dependencies (`build.gradle.kts`)

```kotlin
plugins {
    id("inquisitor.spring-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // in-memory storage — no DB setup required for demo
    // swap to spring-boot-starter-data-jpa + h2 if SQL scenario testing is desired

    testImplementation(project(":inquisitor-spring-boot-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

## Main Source (`src/main/java/io/inquisitor/demo/`)

### Domain

```
model/
  User.java     record(Long id, String name, String email)
  Order.java    record(Long id, Long userId, String product, BigDecimal price, Instant createdAt)
```

### Repositories (in-memory, thread-safe)

```
repository/
  UserRepository.java    ConcurrentHashMap-backed; findById, findAll, save, delete
  OrderRepository.java   same pattern; findByUserId
```

### Controllers

**`UserController`** — `@RestController @RequestMapping("/users")`

| Method | Path | Description | Success | Error |
|---|---|---|---|---|
| POST | `/users` | Create user | 201 + body | 400 if name/email blank |
| GET | `/users/{id}` | Fetch user | 200 | 404 `ProblemDetail` |
| GET | `/users` | List all users | 200 array | — |
| DELETE | `/users/{id}` | Delete user | 204 | 404 `ProblemDetail` |

**`OrderController`** — `@RestController`

| Method | Path | Description | Success | Error |
|---|---|---|---|---|
| POST | `/orders` | Create order (body includes `userId`) | 201 + body | 400, 404 |
| GET | `/orders/{id}` | Fetch order | 200 | 404 |
| GET | `/users/{userId}/orders` | List user's orders | 200 array | 404 if user missing |

### Error handling

`@ControllerAdvice` returning RFC 9457 `ProblemDetail` for `NotFoundException` (404) and `MethodArgumentNotValidException` (400).

## Configuration (`src/main/resources/application.yml`)

```yaml
spring:
  application:
    name: inquisitor-demo
server:
  port: 8080
```

## Test Sources

### `src/test/resources/application.yml`
```yaml
inquisitor:
  base-url: http://localhost:${local.server.port}
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:test-key}
      # use a mock/stub model in CI to avoid real API calls
```

### `src/test/resources/scenarios/`

**`create-user.md`**
```markdown
# Create and retrieve a user

Register a new user with name "Alice" and email "alice@example.com" via POST /users.
After successful creation, retrieve the user by the ID returned in the response body.
Verify the retrieved user has name "Alice" and email "alice@example.com".
```

**`delete-nonexistent-user.md`**
```markdown
# Delete a non-existent user

Attempt to delete a user with ID 99999 via DELETE /users/99999.
Verify the response status is 404 and the body contains an error message.
```

**`list-users.md`**
```markdown
# List multiple users

Create two users: one named "Bob" and one named "Carol".
Then call GET /users and verify the response contains at least both users.
```

**`create-order-for-user.md`**
```markdown
# Create an order linked to a user

First create a user named "Dave".
Then create an order for that user with product "Widget" and price 9.99.
Retrieve the order via GET /orders/{id} and verify the userId matches Dave's ID and the product is "Widget".
```

**`user-not-found.md`**
```markdown
# Fetch a non-existent user

Call GET /users/99999.
Verify the HTTP status is 404 and the response body is a valid RFC 9457 problem detail with type and title fields.
```

### `ScenarioSuiteTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@InquisitorTest(scenarioDirs = "classpath:scenarios/")
class ScenarioSuiteTest {}
```

Each `.md` file in `scenarios/` becomes a separate named JUnit test case.

## Verification

```bash
# Start demo app manually
./gradlew :inquisitor-demo:bootRun

# Run all scenario tests (requires OPENAI_API_KEY or mock model configured)
OPENAI_API_KEY=sk-... ./gradlew :inquisitor-demo:test
```

Expected output in Gradle test report: 5 tests named after H1 headings in each `.md` file, all green.

## Notes / Open Questions

- In CI without a real OpenAI key, tests will fail unless a mock `ChatClient` is provided. Consider adding a `@TestConfiguration` that substitutes a scripted `ChatClient` returning canned JSON for each scenario.
- Auto-increment ID strategy for in-memory repo: use `AtomicLong`. Keep it simple — no JPA.
- `BigDecimal` for price: use `@JsonSerialize` / `@JsonDeserialize` to avoid precision issues in JSON.
- Should the demo app have a Swagger/OpenAPI UI? Nice-to-have, not required for validation.
