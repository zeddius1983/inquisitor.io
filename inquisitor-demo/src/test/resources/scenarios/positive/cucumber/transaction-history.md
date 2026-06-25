# Transaction history reflects all operations in order

Feature: every deposit and withdrawal is recorded, and the transaction history is
returned newest-first.

## Open an account for Eve

- **When** a new USD account is opened for Eve via `POST /accounts`
- **Then** the account is created with `201` and a numeric `id`
- **And** its `balance` starts at `0.00`

## Record two deposits and a withdrawal

- **Given** Eve's account
- **When** `200.00` is deposited, then `50.00` is deposited (each via
  `POST /accounts/{id}/deposits`), then `30.00` is withdrawn (via
  `POST /accounts/{id}/withdrawals`), in that order
- **Then** each operation returns `201 Created`
- **And** her `balance` settles at `220.00`

## Retrieve the transaction history

- **When** Eve's transactions are requested via `GET /accounts/{id}/transactions`
- **Then** the response is `200 OK`
- **And** exactly **three** transactions are returned, newest-first: the `30.00`
  withdrawal, then the `50.00` deposit, then the `200.00` deposit
