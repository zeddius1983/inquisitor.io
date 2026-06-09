# Task 02 — `inquisitor-demo` Module

## Goal

Implement a simple Spring Boot 4 REST banking service backed by PostgreSQL that serves as the end-to-end validation target for Inquisitor. The domain is intentionally minimal — accounts, transactions, and transfers — but the business rules are rich enough to produce varied, meaningful scenario files that exercise the full LLM orchestrator loop.

## Dependencies (`build.gradle.kts`)

```kotlin
plugins {
    id("inquisitor.spring-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":inquisitor-harness-junit-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.testcontainers:postgresql")
    testRuntimeOnly("org.testcontainers:junit-jupiter")
}
```

## Database Schema (`src/main/resources/db/migration/V1__init.sql`)

```sql
CREATE TABLE account (
    id         BIGSERIAL    PRIMARY KEY,
    owner      VARCHAR(100) NOT NULL,
    currency   CHAR(3)      NOT NULL DEFAULT 'USD',
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE transaction (
    id         BIGSERIAL    PRIMARY KEY,
    account_id BIGINT       NOT NULL REFERENCES account(id),
    type       VARCHAR(20)  NOT NULL,  -- DEPOSIT | WITHDRAWAL | TRANSFER_IN | TRANSFER_OUT
    amount     NUMERIC(19,4) NOT NULL,
    reference  VARCHAR(36),            -- shared UUID for paired transfer rows
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Balance is a cached column on `account`, updated atomically within a `@Transactional` service method. The `transaction` table is the append-only audit ledger. `version` enables optimistic locking to prevent lost updates under concurrent transfers.

## Main Source (`src/main/java/io/inquisitor/demo/`)

### Domain

```
model/
  Account.java      record — id, owner, currency, balance, version, createdAt
                    annotated with @Id, @Version for Spring Data JDBC
  Transaction.java  record — id, accountId, type (TransactionType enum), amount, reference, createdAt
  TransactionType.java  enum — DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT
```

### Repositories

```
repository/
  AccountRepository.java      CrudRepository<Account, Long>
  TransactionRepository.java  CrudRepository<Transaction, Long>
                               + findByAccountIdOrderByCreatedAtDesc(Long accountId)
```

### Service

**`AccountService`** — all mutating operations run inside `@Transactional`

- `createAccount(owner, currency)` → `Account`
- `deposit(accountId, amount)` — appends `DEPOSIT` row, increments balance; throws `AccountNotFoundException` if missing
- `withdraw(accountId, amount)` — appends `WITHDRAWAL` row, decrements balance; throws `InsufficientFundsException` if `balance < amount`
- `transfer(fromId, toId, amount)` — debit source (`TRANSFER_OUT`), credit target (`TRANSFER_IN`), both rows share a random `reference` UUID; throws `InsufficientFundsException` or `AccountNotFoundException` as appropriate

### Controllers

**`AccountController`** — `@RestController @RequestMapping("/accounts")`

| Method | Path | Description | Success | Error |
|---|---|---|---|---|
| POST | `/accounts` | Open a new account | 201 + body | 400 if owner blank |
| GET | `/accounts/{id}` | Fetch account (with current balance) | 200 | 404 `ProblemDetail` |
| GET | `/accounts/{id}/transactions` | List transactions newest-first | 200 array | 404 |

**`TransferController`** — `@RestController @RequestMapping("/transfers")`

| Method | Path | Description | Success | Error |
|---|---|---|---|---|
| POST | `/transfers` | Execute a transfer `{fromId, toId, amount}` | 201 + transfer summary | 400, 404, 422 |

### Error handling

`@ControllerAdvice` returning RFC 9457 `ProblemDetail`:
- `AccountNotFoundException` → 404
- `InsufficientFundsException` → 422
- `MethodArgumentNotValidException` → 400

## Configuration

### `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: inquisitor-demo
  datasource:
    url: jdbc:postgresql://localhost:5432/inquisitor_demo
    username: ${DB_USER:demo}
    password: ${DB_PASS:demo}
  flyway:
    enabled: true
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
```

Testcontainers starts a real PostgreSQL instance automatically via Spring Boot's `@ServiceConnection` support — no manual datasource override needed.

### `src/test/resources/scenarios/`

**`open-account-and-deposit.md`**
```markdown
# Open an account and make a deposit

Open a new account for owner "Alice" with currency USD via POST /accounts.
Deposit 500.00 into the new account via POST /transfers (or a dedicated deposit endpoint).
Retrieve the account via GET /accounts/{id} and verify the balance is 500.00.
```

**`transfer-between-accounts.md`**
```markdown
# Transfer funds between two accounts

Open two accounts: one for "Bob" funded with 1000.00 and one for "Carol" funded with 1000.00.
Transfer 250.00 from Bob's account to Carol's account via POST /transfers.
Verify Bob's balance is 750.00 and Carol's balance is 1250.00.
```

**`overdraft-rejected.md`**
```markdown
# Overdraft attempt is rejected

Open an account for "Dave" and deposit 100.00.
Attempt to transfer 500.00 from Dave's account to any other account.
Verify the response status is 422 and the body is a valid RFC 9457 problem detail.
Verify Dave's balance is still 100.00 after the failed transfer.
```

**`transaction-history.md`**
```markdown
# Transaction history reflects all operations in order

Open an account for "Eve" and perform three operations: deposit 200.00, deposit 50.00, withdraw 30.00.
Retrieve the transaction list via GET /accounts/{id}/transactions.
Verify there are exactly three transactions and they appear newest-first.
Verify the final account balance is 220.00.
```

**`account-not-found.md`**
```markdown
# Fetch a non-existent account

Call GET /accounts/99999.
Verify the HTTP status is 404 and the response body is a valid RFC 9457 problem detail with type and title fields.
```

### `ScenarioSuiteTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@InquisitorTest(scenarioDirs = "classpath:scenarios/")
class ScenarioSuiteTest {}
```

Each `.md` file becomes a separate named JUnit test case derived from its H1 heading.

## Verification

```bash
# Start demo app (requires local PostgreSQL or Docker)
./gradlew :inquisitor-demo:bootRun

# Run all scenario tests via Testcontainers (no external DB needed)
OPENAI_API_KEY=sk-... ./gradlew :inquisitor-demo:test
```

Expected output: 5 tests named after H1 headings, all green.

## Notes

- `@Version` on `Account` provides optimistic locking — concurrent transfers to the same account will retry rather than silently lose an update.
- Flyway manages schema; no `ddl-auto` needed.
- In CI without a real OpenAI key, add a `@TestConfiguration` that substitutes a scripted `ChatClient` returning canned responses.
