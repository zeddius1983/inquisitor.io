# Logging

## Implementation Choice

| Stack | When to use |
|---|---|
| **Logback + SLF4J** (default) | All standard services — Spring Boot's default, zero config to get started |
| Log4j2 + SLF4J | Only when you need very high-throughput async logging (100k+ events/sec); requires excluding `spring-boot-starter-logging` and adding `spring-boot-starter-log4j2` |
| java.util.logging (JUL) | Never — route through SLF4J bridge if a dependency uses it |

Spring Boot pulls in Logback via `spring-boot-starter-logging`, which every `spring-boot-starter-*` includes transitively. **No explicit logging dependency is needed.**

## Usage — Always via SLF4J + Lombok `@Slf4j`

```java
@Service
@RequiredArgsConstructor
@Slf4j                          // generates: private static final Logger log = LoggerFactory.getLogger(...)
public class OrderService {

    private final OrderRepository orderRepository;

    public Order create(CreateOrderRequest req) {
        log.debug("Creating order for customer {}", req.customerId());

        val order = orderRepository.save(Order.create(req.customerId(), req.items()));

        // SLF4J 2.x fluent API — preferred for structured key-value pairs
        log.atInfo()
            .addKeyValue("orderId", order.id())
            .addKeyValue("customerId", order.customerId())
            .addKeyValue("itemCount", order.items().size())
            .log("Order created");

        return order;
    }

    public Order updateStatus(UUID id, OrderStatus newStatus) {
        return orderRepository.findById(id)
            .map(o -> o.withStatus(newStatus))
            .map(orderRepository::save)
            .inspect(o -> log.atInfo()
                .addKeyValue("orderId", o.id())
                .addKeyValue("status", newStatus)
                .log("Order status updated"))
            .orElseThrow(() -> {
                log.warn("Order not found: {}", id);
                return new EntityNotFoundException("Order not found: " + id);
            });
    }
}
```

## Structured JSON Logging (Spring Boot 4.x Built-in)

Spring Boot 3.4+ / 4.x has built-in structured logging — no extra library or `logback-spring.xml` needed:

```yaml
# application-prod.yml
logging:
  structured:
    format:
      console: ecs          # Elastic Common Schema — best for ELK / OpenSearch
      # console: logstash   # Logstash JSON format
      # console: gelf       # Graylog Extended Log Format
  level:
    root: INFO
    io.inquisitor: INFO
```

```yaml
# application-local.yml — human-readable in dev
logging:
  structured:
    format:
      console: none         # disable structured output locally
  pattern:
    console: "%clr(%d{HH:mm:ss.SSS}){faint} %clr(%-5level) %clr(${PID:- }){magenta} %clr(---){faint} [%15.15t] %clr(%-40.40logger{39}){cyan} : %m%n%xwEx"
  level:
    root: INFO
    io.inquisitor: DEBUG
    org.springframework.jdbc.core: DEBUG
    org.flywaydb: DEBUG
```

## `logback-spring.xml` — When You Need Fine-Grained Control

Use the Spring-aware `logback-spring.xml` (not `logback.xml`) so Spring can apply `<springProfile>` blocks and expand `${spring.application.name}`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <springProperty scope="context" name="appName" source="spring.application.name"/>

    <!-- ── Console appender (local / dev) ──────────────────────────────── -->
    <springProfile name="local,unitTest">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %-5level [%15.15t] %-40.40logger{39} : %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="io.inquisitor" level="DEBUG"/>
    </springProfile>

    <!-- ── JSON console (prod / staging) ───────────────────────────────── -->
    <!-- Only needed if Spring Boot's built-in structured logging is insufficient -->
    <springProfile name="prod,staging">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"app":"${appName}"}</customFields>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

</configuration>
```

When using `logback-spring.xml`, add the Logstash encoder only if needed:
```kotlin
// build.gradle.kts — only if using logback-spring.xml with LogstashEncoder
implementation("net.logstash.logback:logstash-logback-encoder:8.x")
```

> Prefer Spring Boot's built-in `logging.structured.format.console=ecs` over a custom `logback-spring.xml` unless you need file appenders, rolling policies, or very custom encoders.

## MDC — Correlation IDs Across Logs

MDC (Mapped Diagnostic Context) attaches request-scoped key-value pairs to every log line — essential for tracing a single request across log entries.

```java
// Servlet filter — runs once per request
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID = "correlationId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {

        val correlationId = Optional
            .ofNullable(request.getHeader("X-Correlation-Id"))
            .orElse(UUID.randomUUID().toString());

        MDC.put(CORRELATION_ID, correlationId);
        response.setHeader("X-Correlation-Id", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();    // always clear — threads are reused (even virtual ones)
        }
    }
}
```

Include `%X{correlationId}` in log patterns, or rely on Logstash encoder to include all MDC fields automatically in JSON output.

> **Virtual threads and MDC:** SLF4J's `MDC` uses `ThreadLocal`, which is scoped to the carrier thread and does not automatically propagate into new virtual threads spawned inside a request. If you `CompletableFuture.supplyAsync(...)` inside a request, propagate MDC manually or use Spring's `TaskDecorator`.

```java
// Propagate MDC into async tasks
@Configuration
public class AsyncConfig {

    @Bean
    public Executor asyncExecutor() {
        val executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(runnable -> {
            val context = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (context != null) MDC.setContextMap(context);
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
```

## Log Level Guidelines

| Level | When to use |
|---|---|
| `ERROR` | Unrecoverable failure; requires immediate attention |
| `WARN` | Recoverable issue; degraded behaviour; unexpected but handled state |
| `INFO` | Normal business events: order created, payment processed, user logged in |
| `DEBUG` | Detailed flow for diagnosing issues; SQL, cache hits/misses, external calls |
| `TRACE` | Fine-grained internals; rarely used in production |

## What Not to Log

```java
// NEVER log sensitive data
log.debug("User password: {}", user.password());        // credentials
log.info("Card number: {}", payment.cardNumber());      // PCI data
log.info("Token: {}", jwtToken);                        // auth tokens
log.debug("Request body: {}", requestBody);             // may contain PII

// Mask or omit instead
log.info("Payment processed for card ending {}", last4(payment.cardNumber()));
log.debug("Auth token issued for user {}", userId);
```

## Quick Reference

| Decision | Choice |
|---|---|
| Logging facade | SLF4J (via `@Slf4j`) |
| Implementation | Logback (Spring Boot default) |
| High-throughput alternative | Log4j2 (`spring-boot-starter-log4j2`) |
| Structured JSON | `logging.structured.format.console=ecs` (built-in, Spring Boot 4.x) |
| Custom JSON encoder | `logstash-logback-encoder` (only if built-in is insufficient) |
| Config file | `logback-spring.xml` (Spring-aware) over `logback.xml` |
| Correlation | MDC via `OncePerRequestFilter`; clear in `finally` |
| Sensitive data | Never log credentials, tokens, card numbers, or raw PII |
