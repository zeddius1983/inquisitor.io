# Open an account in a given currency

Feature: an account can be opened in any supported currency and funded. The scenario
runs once per set of parameters — `owner`, `currency` and `deposit` — which are given
with every step.

## Open an account

- **Given** this run's `owner` and `currency`
- **When** an account is opened for the owner in that currency
- **Then** it is created with `201` and a numeric `id`
- **And** its `owner` and `currency` match this run's parameters
- **And** its `balance` reads `0.00`

## Deposit funds

- **When** this run's `deposit` amount is deposited into the account
- **Then** the response is `201 Created`
- **And** the `balance` equals the deposit

## Read the account back

- **When** the account is reloaded
- **Then** the response is `200 OK`
- **And** the `balance` still equals the deposit
- **And** the `currency` is still this run's `currency`
