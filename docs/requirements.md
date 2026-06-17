# Functional Requirements

What the system must do. For how it's structured see
[architecture.md](architecture.md); for stable context see
[../CLAUDE.md](../CLAUDE.md).

There are two products in this repo: the **Inquisitor harness** (the tool) and
the **demo banking service** (its reference consumer / proving ground).

---

## 1. Inquisitor harness

### 1.1 Scenario authoring
- A scenario is a single **markdown** file describing, in natural language, a
  sequence of actions against the application under test and the expected
  outcome.
- Scenario files live under a configurable directory
  (`classpath:scenarios/` by convention) and are discovered automatically.

### 1.2 Execution
- The harness parses each scenario (flexmark) and uses a Spring AI `ChatClient`
  to interpret it and drive the running application over HTTP.
- Each scenario runs as an isolated test case and produces a clear **pass/fail**
  verdict with a human-readable explanation on failure.

### 1.3 Consumer integration
- A consumer adds one test-scoped dependency (`inquisitor-harness-junit-starter`)
  and annotates a test class with `@InquisitorTest(scenarioDirs = …)` alongside
  `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- Wiring is zero-config via Spring Boot autoconfiguration; no manual bean setup.

### 1.4 Reserved
- **Mock server** (`inquisitor-mock`): stub third-party HTTP dependencies for the
  duration of a scenario. Design pending — see [roadmap.md](roadmap.md).

---

## 2. Demo banking service

A minimal banking API that exercises the harness. Base entities: **Account** and
**Transaction**.

### 2.1 Accounts
- **Create account** — `POST /accounts` with `{ owner, currency }`.
  - `owner` required (non-blank); `currency` required, exactly 3 uppercase
    letters (`[A-Z]{3}`).
  - New accounts open with balance `0`. Returns **201 Created**.
- **Get account** — `GET /accounts/{id}`. Returns the account or **404** if not
  found.
- **List/search accounts** — `GET /accounts`. Optional filters `owner`,
  `currency`; paginated (default size 20, sorted by `id`).

### 2.2 Money movement
- **Deposit** — `POST /accounts/{id}/deposits` with `{ amount }`.
  - `amount` required, ≥ `0.01`. Credits the balance, records a `DEPOSIT`
    transaction. Returns **201** with the updated account.
- **Withdraw** — `POST /accounts/{id}/withdrawals` with `{ amount }`.
  - Same amount rules. Rejected with **422** if balance < amount
    (insufficient funds). On success records a `WITHDRAWAL` transaction.
- **Transfer** — `POST /transfers` with `{ fromId, toId, amount }`.
  - Atomic: debits source, credits destination, records paired `TRANSFER_OUT` /
    `TRANSFER_IN` transactions sharing a generated `reference`.
  - Rejected with **422** if the source has insufficient funds; on rejection no
    balances change. Returns **201** with a transfer summary
    (`reference, fromId, toId, amount`).

### 2.3 Transaction history
- **List transactions** — `GET /accounts/{id}/transactions`.
  - Optional filters: `type` (`DEPOSIT|WITHDRAWAL|TRANSFER_IN|TRANSFER_OUT`),
    `from`, `to` (ISO-8601 timestamps).
  - Paginated, **newest-first** (sorted by `createdAt` desc). Returns **404** if
    the account does not exist.

### 2.4 Cross-cutting
- **Money precision** — amounts are `NUMERIC(19,4)` / `BigDecimal`; never floats.
- **Concurrency** — accounts use optimistic locking (`@Version`).
- **Validation errors** — return **400** as an RFC 9457 `ProblemDetail`
  (field-level messages).
- **Error format** — all error responses (`404`, `422`, `400`) are RFC 9457
  problem details with `type` and `title`.

### 2.5 Acceptance scenarios
The demo's behavior is pinned by the markdown scenarios in
`inquisitor-demo/src/test/resources/scenarios/`, which double as the harness's
own acceptance tests:

| Scenario | Asserts |
|----------|---------|
| `open-account-and-deposit` | Open account, deposit 500.00, balance reflects it |
| `transfer-between-accounts` | Transfer 250.00 moves funds correctly between two accounts |
| `overdraft-rejected` | Over-limit transfer returns 422 and leaves balance unchanged |
| `transaction-history` | Three operations recorded, newest-first, balance correct |
| `account-not-found` | `GET` unknown account returns 404 RFC 9457 problem detail |
