# Database state matches the API

Feature: the SQL tool and the HTTP API agree — a row seeded directly in the
database is visible through the API, and a deposit made through the API is visible
in the database.

## Seed an account with SQL

- **Given** the database is reachable
- **When** an account for Frank in EUR with a zero balance is inserted directly
  with SQL, returning its id:

  ```sql
  INSERT INTO account (owner, currency, balance) VALUES ('Frank', 'EUR', 0) RETURNING id;
  ```

- **Then** exactly one row is inserted
- **And** the new account `id` is returned

## Read the seeded account through the API

- **When** Frank's account is fetched via `GET /accounts/{id}`
- **Then** the response is `200 OK`
- **And** the `owner` is Frank, the `currency` is EUR, and the `balance` is `0.00`

## Deposit through the API

- **When** `75.00` is deposited into Frank's account via `POST /accounts/{id}/deposits`
- **Then** the response is `201 Created`
- **And** his `balance` reads `75.00`

## Verify the persisted transaction with SQL

- **When** the `transaction` table is queried for Frank's account:

  ```sql
  SELECT type, amount FROM transaction WHERE account_id = {frankId};
  ```

- **Then** exactly one row is returned
- **And** its `type` is `DEPOSIT` and its `amount` is `75.00`
