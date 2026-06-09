# JPA / Hibernate Optimization

> **Fallback reference.** Use Spring Data JDBC by default (`references/spring-data-jdbc.md`).
> Load this reference only when JPA/Hibernate is explicitly required by the user or the project.

## Dependencies (Gradle Kotlin DSL)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
```

## Optimized Entity Design

```java
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_created_at", columnList = "created_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
```

## Repository with Optimized Queries

```java
public interface UserRepository extends JpaRepository<User, UUID> {

    // N+1 prevention — fetch association in one query
    @EntityGraph(attributePaths = {"orders", "department"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithOrders(@Param("id") UUID id);

    // DTO projection — limit fetched columns
    @Query("""
        SELECT new com.example.dto.UserSummary(u.id, u.email, u.username, COUNT(o))
        FROM User u LEFT JOIN u.orders o
        WHERE u.active = true
        GROUP BY u.id, u.email, u.username
        """)
    List<UserSummary> findActiveUsersSummaries();

    // Pagination with optimised count query
    @Query(
        value = "SELECT u FROM User u WHERE u.active = true",
        countQuery = "SELECT COUNT(u) FROM User u WHERE u.active = true"
    )
    Page<User> findActiveUsers(Pageable pageable);

    // Bulk update
    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.createdAt < :before")
    int deactivateCreatedBefore(@Param("before") Instant before);
}
```

## DTO Projections

```java
// Record-based (constructor expression)
public record UserSummary(UUID id, String email, String username, Long orderCount) {}

// Interface-based (Spring proxy)
public interface UserProjection {
    UUID getId();
    String getEmail();
    String getUsername();
}

// Dynamic projection
<T> List<T> findByActive(boolean active, Class<T> type);
```

## Batch Operations

```java
@Service
@RequiredArgsConstructor
public class UserBatchService {

    private final EntityManager em;

    @Transactional
    public void batchInsert(List<User> users) {
        for (int i = 0; i < users.size(); i++) {
            em.persist(users.get(i));
            if (i % 50 == 49) {
                em.flush();
                em.clear();
            }
        }
        em.flush();
        em.clear();
    }
}
```

## `application.yml` Tuning

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc.batch_size: 25
        order_inserts: true
        order_updates: true
        default_batch_fetch_size: 25
        cache:
          use_second_level_cache: true
          region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

## Performance Monitoring

```java
@Component
@Aspect
@Slf4j
public class SlowQueryAspect {

    @Around("@annotation(org.springframework.data.jpa.repository.Query)")
    public Object log(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long ms = System.currentTimeMillis() - start;
            if (ms > 500) {
                log.warn("Slow query: {} took {}ms", pjp.getSignature(), ms);
            }
        }
    }
}
```

## Quick Reference

| Pattern | Use Case |
|---|---|
| `@EntityGraph` | Prevent N+1 on a specific query |
| `JOIN FETCH` | Eager-fetch in JPQL |
| DTO projection | Read-only queries — fetch only needed columns |
| `@BatchSize` | Batch-fetch lazy collections |
| `@Modifying` | Bulk update/delete |
| Criteria API | Dynamic queries |
| `readOnly = true` | Performance hint on read-only transactions |
| Second-level cache | Frequently read, rarely written entities |
