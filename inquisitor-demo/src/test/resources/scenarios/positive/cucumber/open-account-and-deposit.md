# Open an account and make a deposit

Feature: the basic account lifecycle — open an account, fund it, and confirm the
balance persisted.

## Open the account

- **Given** Alice has no account yet
- **When** she opens a new account in USD via `POST /accounts`
- **Then** the response is `201 Created`
- **And** the returned account has a numeric `id`, a `balance` of `0.00`, and
  `currency` USD

## Make a deposit

- **Given** Alice's newly opened account
- **When** `500.00` is deposited into it via the account's deposits endpoint
- **Then** the response is `201 Created`
- **And** her `balance` now reads `500.00`

## Read the account back

- **Given** the deposit has been made
- **When** her account is reloaded via `GET /accounts/{id}`
- **Then** the response is `200 OK`
- **And** the `balance` is still `500.00` and the `currency` is still USD
