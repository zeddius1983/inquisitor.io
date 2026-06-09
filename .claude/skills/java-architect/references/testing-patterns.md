# Testing Patterns (Spring Boot 4.x, Java 26)

## Mocks vs TestContainers — When to Use Which

| Scenario | Approach |
|---|---|
| Unit test — pure business logic | `@Mock` / `@InjectMocks` (Mockito) |
| Controller layer (`@WebMvcTest`) | `@MockBean` the service; no DB needed |
| Repository / data-access test | **TestContainers** — real DB, no mocks |
| Full integration test | **TestContainers** — `@SpringBootTest` + real DB |
| External HTTP dependency | Mock with `MockServer` or `WireMock` |

> **Rule:** never mock the database or the repository layer. A test that mocks `OrderRepository` proves nothing about your SQL. Use a real Postgres container via `@ServiceConnection` instead.

## Unit Testing with JUnit 5 + Mockito

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderMapper orderMapper;
    @InjectMocks OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = Order.create(UUID.randomUUID(), List.of(
            new OrderItem("SKU-1", 2, BigDecimal.valueOf(9.99))
        ));
    }

    @Test
    @DisplayName("should find order by ID")
    void shouldFindOrderById() {
        when(orderRepository.findById(testOrder.id())).thenReturn(Optional.of(testOrder));

        val result = orderService.findById(testOrder.id());

        assertThat(result).isPresent().contains(testOrder);
        verify(orderRepository).findById(testOrder.id());
    }

    @Test
    @DisplayName("should throw when order not found")
    void shouldThrowWhenNotFound() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(UUID.randomUUID()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("should accept all valid statuses")
    void shouldAcceptAllStatuses(OrderStatus status) {
        assertThat(status).isNotNull();
    }
}
```

## Integration Testing — `@ServiceConnection` (Spring Boot 4.x)

Spring Boot 4 auto-wires Testcontainers via `@ServiceConnection` — no manual property overrides needed:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired TestRestTemplate restTemplate;
    @Autowired OrderRepository orderRepository;

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
    }

    @Test
    void shouldCreateAndRetrieveOrder() {
        val request = new CreateOrderRequest(UUID.randomUUID(), List.of(
            new OrderItemRequest("SKU-1", 1, BigDecimal.TEN)
        ));

        val created = restTemplate.postForEntity("/api/v1/orders", request, OrderDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        val fetched = restTemplate.getForEntity(
            "/api/v1/orders/" + created.getBody().id(), OrderDto.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

## Shared Container Base Class

```java
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-alpine").withReuse(true);
}

// Usage:
@SpringBootTest
class OrderRepositoryTest extends AbstractIntegrationTest { ... }
```

## Spring Data JDBC Repository Tests

```java
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired OrderRepository orderRepository;

    @Test
    void shouldPersistAndFindOrder() {
        val order = Order.create(UUID.randomUUID(), List.of(
            new OrderItem("SKU-1", 1, BigDecimal.TEN)
        ));
        val saved = orderRepository.save(order);

        val found = orderRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().items()).hasSize(1);
    }

    @Test
    void shouldFindByCustomerId() {
        val customerId = UUID.randomUUID();
        orderRepository.save(Order.create(customerId, List.of()));
        orderRepository.save(Order.create(UUID.randomUUID(), List.of()));

        assertThat(orderRepository.findByCustomerId(customerId)).hasSize(1);
    }
}
```

## Controller Tests — `@WebMvcTest`

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrderService orderService;

    @Test
    @WithMockUser
    void shouldGetOrderById() throws Exception {
        val order = new OrderDto(UUID.randomUUID(), OrderStatus.PENDING, List.of());
        when(orderService.findById(order.id())).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/v1/orders/{id}", order.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenNotFound() throws Exception {
        when(orderService.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }
}
```

## Reactive Controller Tests — `@WebFluxTest`

```java
@WebFluxTest(UserController.class)
class UserControllerTest {

    @Autowired WebTestClient webTestClient;
    @MockBean UserService userService;

    @Test
    @WithMockUser
    void shouldStreamUsers() {
        when(userService.findAll()).thenReturn(Flux.just(
            new UserDto(UUID.randomUUID(), "alice@example.com"),
            new UserDto(UUID.randomUUID(), "bob@example.com")
        ));

        webTestClient.get().uri("/api/users")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(UserDto.class)
            .hasSize(2);
    }
}
```

## Test Data Builders

```java
public final class OrderTestFixtures {

    public static Order pendingOrder() {
        return Order.create(UUID.randomUUID(), List.of(defaultItem()));
    }

    public static Order pendingOrder(UUID customerId) {
        return Order.create(customerId, List.of(defaultItem()));
    }

    public static OrderItem defaultItem() {
        return new OrderItem("SKU-TEST", 1, BigDecimal.valueOf(9.99));
    }

    private OrderTestFixtures() {}
}
```

## TestConfiguration for Dev Services

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
```

## Security Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecuredEndpointTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccess() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());
    }
}
```

## Quick Reference

| Annotation | Purpose |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Mockito JUnit 5 integration |
| `@SpringBootTest` | Full application context |
| `@WebMvcTest` | MVC layer only (blocking) |
| `@WebFluxTest` | WebFlux layer only (reactive) |
| `@JdbcTest` | Spring Data JDBC slice |
| `@MockBean` | Replace Spring bean with mock |
| `@ServiceConnection` | Auto-wire Testcontainers to auto-config |
| `@WithMockUser` | Inject mock security principal |
| `StepVerifier` | Assert reactive streams |
| `assertThat()` | AssertJ fluent assertions |
