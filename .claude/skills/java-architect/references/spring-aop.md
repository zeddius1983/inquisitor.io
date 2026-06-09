# Spring AOP

## When to Use AOP

AOP is the right tool when the same cross-cutting behaviour needs to apply across many classes without modifying each one:

| Good use cases | Poor use cases |
|---|---|
| Logging / audit trails | Complex business logic |
| Timing / metrics instrumentation | Logic that needs visibility in the class |
| Security / authorisation checks | Anything that should be unit-tested in isolation |
| Transaction demarcation | Simple one-off concerns |
| Retry / circuit-breaker wrapping | |
| Input/output validation | |

Prefer Spring's built-in aspects (`@Transactional`, `@Cacheable`, `@Async`) before writing a custom one.

---

## Enabling AspectJ Auto-Proxy

Spring Boot enables `@EnableAspectJAutoProxy` automatically when `spring-boot-starter-aop` is on the classpath. No explicit annotation is required in a Spring Boot application.

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-aop")
}
```

For non-Boot projects:
```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {}
```

---

## Declaring an Aspect

An aspect is a regular Spring bean annotated with both `@Aspect` and `@Component`:

```java
@Aspect
@Component
@Slf4j
public class AuditAspect {
    // pointcuts and advice declared here
}
```

---

## Pointcut Expressions

### Reusable Pointcut Declarations

Declare named pointcuts with empty methods annotated `@Pointcut` and compose them:

```java
@Aspect
@Component
public class CommonPointcuts {

    @Pointcut("execution(* io.inquisitor..*(..))")
    public void inquisitorCode() {}

    @Pointcut("within(io.inquisitor..application.service..*)")
    public void serviceLayer() {}

    @Pointcut("within(io.inquisitor..presentation.rest..*)")
    public void restLayer() {}

    @Pointcut("serviceLayer() || restLayer()")
    public void applicationBoundary() {}
}
```

### Key Designators

```java
// execution — primary designator; matches method signatures
"execution(public * io.inquisitor..*(..))"          // any public method in the project
"execution(* io.inquisitor..service.*.*(..))"        // any method in a service package
"execution(* io.inquisitor..OrderService.create(..))" // specific method

// within — limits to types in a package
"within(io.inquisitor..service.*)"                   // types in service package
"within(io.inquisitor..*)"                           // all types in project

// @annotation — matches methods carrying a given annotation (most useful for custom aspects)
"@annotation(io.inquisitor.common.aop.Audited)"
"@annotation(org.springframework.transaction.annotation.Transactional)"

// @within — matches all methods in types carrying a given annotation
"@within(org.springframework.stereotype.Service)"

// args — matches by runtime argument type
"args(java.util.UUID, ..)"                           // first arg is UUID

// bean — Spring-specific; matches named beans
"bean(*Service)"                                     // any bean whose name ends with Service

// Combining with &&, ||, !
"execution(* io.inquisitor..service.*.*(..)) && !execution(* io.inquisitor..service.*.*Async(..))"
```

---

## Advice Types

### `@Around` — Most Powerful; Use When You Need to Control Execution

```java
@Around("io.inquisitor.common.aop.CommonPointcuts.serviceLayer()")
public Object logAndTime(ProceedingJoinPoint pjp) throws Throwable {
    val signature = pjp.getSignature().toShortString();
    val start     = System.currentTimeMillis();

    log.debug("→ {}", signature);
    try {
        val result = pjp.proceed();          // invoke the real method
        log.debug("← {} ({}ms)", signature, System.currentTimeMillis() - start);
        return result;
    } catch (Exception ex) {
        log.error("✗ {} threw {}", signature, ex.getClass().getSimpleName());
        throw ex;                            // always rethrow unless intentionally swallowing
    }
}
```

### `@Before` — Side Effect Before Method; Cannot Prevent Execution

```java
@Before("@annotation(io.inquisitor.common.aop.RequiresRole) && args(request, ..)")
public void checkRole(JoinPoint jp, Object request) {
    val annotation = ((MethodSignature) jp.getSignature())
        .getMethod().getAnnotation(RequiresRole.class);
    securityService.assertRole(annotation.value());
}
```

### `@AfterReturning` — Inspect or Transform the Return Value

```java
@AfterReturning(
    pointcut = "io.inquisitor.common.aop.CommonPointcuts.serviceLayer()",
    returning = "result"
)
public void publishDomainEvent(JoinPoint jp, Object result) {
    if (result instanceof Order order) {         // pattern matching
        eventPublisher.publishEvent(new OrderCreatedEvent(order));
    }
}
```

### `@AfterThrowing` — React to Exceptions Without Swallowing Them

```java
@AfterThrowing(
    pointcut = "io.inquisitor.common.aop.CommonPointcuts.applicationBoundary()",
    throwing = "ex"
)
public void recordFailure(JoinPoint jp, Exception ex) {
    meterRegistry.counter("app.errors",
        "method", jp.getSignature().getName(),
        "exception", ex.getClass().getSimpleName()
    ).increment();
}
```

### `@After` — Finally Block Equivalent; Runs on Both Return and Throw

```java
@After("@annotation(io.inquisitor.common.aop.HoldLock)")
public void releaseLock(JoinPoint jp) {
    lockManager.release(jp.getSignature().getName());
}
```

---

## Custom Annotations as Pointcuts

This is the most idiomatic pattern: define a marker annotation, then write an aspect that triggers on it. The annotation makes intent explicit at the call site.

### Step 1 — Define the Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)  // must be RUNTIME for Spring AOP
@Documented
public @interface Audited {
    String action() default "";
    AuditLevel level() default AuditLevel.INFO;
}
```

### Step 2 — Write the Aspect with Annotation Binding

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditRepository auditRepository;
    private final SecurityUtils securityUtils;

    // Bind the annotation instance directly as a parameter — Spring resolves it automatically
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        val actor  = securityUtils.currentUsername().orElse("system");
        val method = pjp.getSignature().toShortString();
        val action = audited.action().isBlank() ? method : audited.action();

        try {
            val result = pjp.proceed();
            auditRepository.save(AuditEntry.success(actor, action, pjp.getArgs()));
            return result;
        } catch (Exception ex) {
            auditRepository.save(AuditEntry.failure(actor, action, ex.getMessage()));
            throw ex;
        }
    }
}
```

### Step 3 — Use at the Call Site

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    @Audited(action = "order.create", level = AuditLevel.INFO)
    public Order create(CreateOrderRequest request) { ... }

    @Audited(action = "order.cancel", level = AuditLevel.WARN)
    public void cancel(UUID orderId) { ... }
}
```

---

## Practical Custom Aspects

### Metrics / Timing Aspect

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {
    String value();                  // metric name
    String[] tags() default {};      // "key=value" pairs
}

@Aspect
@Component
@RequiredArgsConstructor
public class TimedAspect {

    private final MeterRegistry meterRegistry;

    @Around("@annotation(timed)")
    public Object time(ProceedingJoinPoint pjp, Timed timed) throws Throwable {
        val sample = Timer.start(meterRegistry);
        try {
            return pjp.proceed();
        } finally {
            sample.stop(Timer.builder(timed.value())
                .tags(parseTags(timed.tags()))
                .register(meterRegistry));
        }
    }

    private Iterable<Tag> parseTags(String[] raw) {
        return Arrays.stream(raw)
            .map(t -> t.split("=", 2))
            .map(kv -> Tag.of(kv[0], kv[1]))
            .toList();
    }
}
```

### Idempotency / Deduplication Aspect

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    String keyExpression();          // SpEL expression for the idempotency key
}

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private final IdempotencyStore store;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(idempotent)")
    public Object enforce(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        val key = resolveKey(idempotent.keyExpression(), pjp);
        return store.computeIfAbsent(key, () -> {
            try { return pjp.proceed(); }
            catch (Throwable t) { throw new RuntimeException(t); }
        });
    }

    private String resolveKey(String expression, ProceedingJoinPoint pjp) {
        val context = new MethodBasedEvaluationContext(
            pjp.getTarget(), ((MethodSignature) pjp.getSignature()).getMethod(),
            pjp.getArgs(), new DefaultParameterNameDiscoverer()
        );
        return parser.parseExpression(expression).getValue(context, String.class);
    }
}

// Usage:
@Idempotent(keyExpression = "#request.idempotencyKey")
public Order create(CreateOrderRequest request) { ... }
```

---

## Built-in Spring Aspects (Use Before Writing Custom Ones)

```java
// Transaction management — applies to public methods by default
@Transactional                              // read-write transaction
@Transactional(readOnly = true)             // optimised for reads
@Transactional(propagation = REQUIRES_NEW)  // independent nested transaction
@Transactional(noRollbackFor = BusinessValidationException.class)

// Caching (requires a CacheManager bean)
@Cacheable(value = "orders", key = "#id")       // return cached or invoke and cache
@CachePut(value = "orders", key = "#order.id")  // always invoke, update cache
@CacheEvict(value = "orders", key = "#id")      // remove from cache

// Async execution (requires @EnableAsync; returns Future/CompletableFuture/void)
@Async
public CompletableFuture<Report> generateReport(UUID id) { ... }

// Scheduling (requires @EnableScheduling)
@Scheduled(cron = "0 0 2 * * *")           // 2am every day
@Scheduled(fixedDelay = 5000)              // 5s after last completion

// Retry (requires spring-retry + @EnableRetry)
// implementation("org.springframework.retry:spring-retry")
@Retryable(retryFor = TransientDataAccessException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 500, multiplier = 2))
public Order save(Order order) { ... }

@Recover
public Order recoverSave(TransientDataAccessException ex, Order order) {
    log.error("Persistent failure saving order {}", order.id(), ex);
    throw new ServiceUnavailableException("Database unavailable");
}
```

---

## JoinPoint API

```java
@Around("...")
public Object example(ProceedingJoinPoint pjp) throws Throwable {
    // Signature
    val sig    = (MethodSignature) pjp.getSignature();
    val method = sig.getMethod();
    val name   = sig.getName();                     // method name
    val type   = sig.getDeclaringType();            // class

    // Arguments
    val args   = pjp.getArgs();                     // Object[]

    // Annotation from the method
    val ann    = method.getAnnotation(Audited.class);

    // Proceed — optionally with modified args
    return pjp.proceed();
    // return pjp.proceed(modifiedArgs);
}
```

---

## Advice Ordering

When multiple aspects apply to the same join point, control order with `@Order`
(lower value = higher precedence = outermost wrapper):

```java
@Aspect @Component @Order(1)  public class SecurityAspect  { ... }  // runs first/outermost
@Aspect @Component @Order(2)  public class TimingAspect    { ... }
@Aspect @Component @Order(3)  public class AuditAspect     { ... }  // runs last/innermost
```

---

## The Self-Invocation Limitation

Spring AOP works through proxies. Calling a method on `this` bypasses the proxy and
therefore bypasses all advice — including `@Transactional`, `@Cacheable`, and your own aspects.

```java
@Service
public class OrderService {

    // BROKEN: @Transactional on findById is ignored when called from create()
    public Order create(CreateOrderRequest req) {
        val existing = findById(req.idempotencyKey()); // direct 'this' call — proxy bypassed
        ...
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) { ... }
}
```

**Solutions (in order of preference):**

```java
// 1. Refactor — extract to a separate Spring bean (cleanest)
@Service @RequiredArgsConstructor
public class OrderCreationService {
    private final OrderQueryService orderQueryService;  // separate bean, goes through proxy

    public Order create(CreateOrderRequest req) {
        val existing = orderQueryService.findById(req.idempotencyKey());
        ...
    }
}

// 2. Self-inject (acceptable when refactoring is disproportionate)
@Service
public class OrderService {
    @Lazy @Autowired private OrderService self;   // @Lazy breaks circular dependency

    public Order create(CreateOrderRequest req) {
        val existing = self.findById(req.idempotencyKey()); // goes through proxy
        ...
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) { ... }
}

// 3. AopContext.currentProxy() — avoid; couples code to Spring AOP internals
```

---

## Proxy Type Reference

| Scenario | Proxy type | Notes |
|---|---|---|
| Bean implements an interface | JDK dynamic proxy (default) | Proxy only intercepts interface methods |
| Bean has no interface | CGLIB subclass proxy | Cannot proxy `final` classes or methods |
| `@EnableAspectJAutoProxy(proxyTargetClass=true)` | Always CGLIB | |
| Spring Boot default | CGLIB | Spring Boot sets `proxyTargetClass=true` by default since 2.x |

Because Spring Boot defaults to CGLIB, **avoid marking service classes or methods `final`** — it silently disables AOP advice on those targets.

---

## Quick Reference

| Annotation | Runs | Can prevent execution? | Can modify return? |
|---|---|---|---|
| `@Before` | Before method | No (throw to abort) | No |
| `@AfterReturning` | After normal return | No | No (observe only) |
| `@AfterThrowing` | After exception | No | No |
| `@After` | Always (finally) | No | No |
| `@Around` | Wraps method | Yes | Yes |

| Pointcut designator | Matches on |
|---|---|
| `execution(...)` | Method signature |
| `within(...)` | Type / package |
| `@annotation(...)` | Annotation on method |
| `@within(...)` | Annotation on type |
| `args(...)` | Runtime argument types |
| `bean(...)` | Spring bean name |
