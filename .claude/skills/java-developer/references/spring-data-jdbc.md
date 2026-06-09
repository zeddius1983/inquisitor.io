# Spring Data JDBC

> **Default data access layer.** Use this reference unless JPA/Hibernate is explicitly requested.
> Spring Data JDBC follows DDD aggregate semantics — no lazy loading, no dirty checking, no session magic.

## Core Concepts

- **Aggregate root** — the only entry point for persistence; one repository per root
- **Owned entities** — entities reachable from the root are part of the same aggregate; they are deleted/recreated on save
- **AggregateReference** — a typed FK to another aggregate (stores the ID only, no join)
- **No lazy loading** — all associations load eagerly; design aggregates to be small and cohesive

## Aggregate Design with Records

```java
@Table("orders")
public record Order(
    @Id UUID id,
    UUID customerId,
    OrderStatus status,
    @MappedCollection(idColumn = "order_id", keyColumn = "position")
    List<OrderItem> items,
    @CreatedDate Instant createdAt,
    @LastModifiedDate Instant updatedAt
) {
    // Factory method — ID assigned here, not by the DB
    public static Order create(UUID customerId, List<OrderItem> items) {
        return new Order(
            UUID.randomUUID(), customerId, OrderStatus.PENDING,
            List.copyOf(items), null, null
        );
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, customerId, newStatus, items, createdAt, updatedAt);
    }
}

// Owned entity — no @Id, no repository
public record OrderItem(
    String productCode,
    int quantity,
    BigDecimal price
) {}
```

## Embedded Value Objects

```java
@Table("customers")
public record Customer(
    @Id UUID id,
    String name,
    @Embedded.Nullable(prefix = "billing_") Address billingAddress,
    @Embedded.Nullable(prefix = "shipping_") Address shippingAddress
) {}

// Address columns are flattened into the customers table
// e.g. billing_street, billing_city, billing_zip
public record Address(String street, String city, String zip) {}
```

## AggregateReference (Cross-Aggregate FK)

```java
@Table("invoices")
public record Invoice(
    @Id UUID id,
    AggregateReference<Customer, UUID> customer, // stores customer_id FK only
    AggregateReference<Order, UUID> order,
    BigDecimal amount,
    Instant issuedAt
) {
    // Resolve the reference when you need the customer:
    // customerRepository.findById(invoice.customer().getId())
}
```

## Repository Interface

```java
public interface OrderRepository extends CrudRepository<Order, UUID> {

    // Derived query
    List<Order> findByCustomerId(UUID customerId);

    // Named query — SQL in @Query
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY created_at DESC LIMIT :limit")
    List<Order> findRecentByStatus(OrderStatus status, int limit);

    // Pageable
    @Query("SELECT * FROM orders WHERE customer_id = :customerId")
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    // Modifying — for updates that don't go through the aggregate
    @Modifying
    @Query("UPDATE orders SET status = :status WHERE id = :id")
    void updateStatus(UUID id, OrderStatus status);

    // Count / exists
    boolean existsByCustomerIdAndStatus(UUID customerId, OrderStatus status);
}
```

## Custom Queries with NamedParameterJdbcTemplate

For queries too complex for derived methods or `@Query`:

```java
@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<OrderSummary> findSummaries(OrderSearchCriteria criteria) {
        val sql = """
            SELECT o.id, o.status, o.created_at, COUNT(i.order_id) AS item_count
            FROM orders o
            LEFT JOIN order_item i ON i.order_id = o.id
            WHERE (:status IS NULL OR o.status = :status::order_status)
              AND (:customerId IS NULL OR o.customer_id = :customerId)
            GROUP BY o.id, o.status, o.created_at
            ORDER BY o.created_at DESC
            """;

        val params = new MapSqlParameterSource()
            .addValue("status", criteria.status() != null ? criteria.status().name() : null)
            .addValue("customerId", criteria.customerId());

        return jdbc.query(sql, params, (rs, _) -> new OrderSummary(
            rs.getObject("id", UUID.class),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", Instant.class),
            rs.getInt("item_count")
        ));
    }
}

public record OrderSummary(UUID id, OrderStatus status, Instant createdAt, int itemCount) {}
public record OrderSearchCriteria(OrderStatus status, UUID customerId) {}
```

## JDBC Auditing

Enable auditing in the application class and add `@CreatedDate` / `@LastModifiedDate` to your records:

```java
@SpringBootApplication
@EnableJdbcAuditing
public class Application { ... }

// Fields in aggregate root:
@CreatedDate  Instant createdAt,
@LastModifiedDate Instant updatedAt,
@CreatedBy  String createdBy,    // requires AuditorAware bean
@LastModifiedBy String modifiedBy
```

## Service Layer Pattern

```java
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    public Order create(CreateOrderRequest req) {
        // functional: build → save in one expression
        return orderRepository.save(Order.create(
            req.customerId(),
            req.items().stream()
                .map(i -> new OrderItem(i.productCode(), i.quantity(), i.price()))
                .toList()
        ));
    }

    public Order updateStatus(UUID id, OrderStatus newStatus) {
        // functional: Optional chain — map transforms, orElseThrow terminates
        return orderRepository.findById(id)
            .map(o -> o.withStatus(newStatus))
            .map(orderRepository::save)
            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
    }

    public void delete(UUID id) {
        // guard first, then delete — clear and simple; no functional overhead needed
        if (!orderRepository.existsById(id)) {
            throw new EntityNotFoundException("Order not found: " + id);
        }
        orderRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> findPendingSummaries() {
        return orderRepository.findByStatus(OrderStatus.PENDING).stream()
            .map(OrderSummary::from)
            .toList();
    }
}
```

## When NOT to Use Spring Data JDBC

Switch to JPA if you need:
- Lazy loading of large object graphs
- Dirty checking / automatic flush without explicit save
- Criteria API for highly dynamic queries
- Inheritance mapping strategies (`SINGLE_TABLE`, `JOINED`)
- Second-level caching (Hibernate L2C)

If any of these are required, load `references/jpa-optimization.md` instead.

## Quick Reference

| Annotation | Purpose |
|---|---|
| `@Table("name")` | Map record/class to table |
| `@Id` | Primary key |
| `@MappedCollection` | One-to-many owned collection |
| `@Embedded` | Flatten value object into parent table |
| `AggregateReference<T,ID>` | FK to another aggregate root |
| `@CreatedDate` / `@LastModifiedDate` | Audit timestamps (requires `@EnableJdbcAuditing`) |
| `@Query` | Custom SQL on repository method |
| `@Modifying` | Mark query as a write operation |
| `CrudRepository` | Base repository (save, findById, delete) |
| `ListCrudRepository` | Same but returns `List` instead of `Iterable` |
