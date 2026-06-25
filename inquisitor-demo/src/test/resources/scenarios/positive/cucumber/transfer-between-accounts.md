# Transfer funds between two accounts

Feature: money can be moved between two funded accounts, and both balances reflect
the transfer.

## Fund Bob's account

- **Given** a new USD account is opened for Bob via `POST /accounts`
- **When** `1000.00` is deposited into it via `POST /accounts/{id}/deposits`
- **Then** the account is created with `201` and a numeric `id`
- **And** after the deposit his `balance` reads `1000.00`

## Fund Carol's account

- **Given** a new USD account is opened for Carol via `POST /accounts`
- **When** `1000.00` is deposited into it via `POST /accounts/{id}/deposits`
- **Then** the account is created with `201` and a numeric `id`
- **And** after the deposit her `balance` reads `1000.00`

## Transfer money from Bob to Carol

- **When** `250.00` is transferred from Bob's account to Carol's via `POST /transfers`
- **Then** the response is `201 Created`
- **And** the returned summary references both account ids and an `amount` of `250.00`

## Confirm the resulting balances

- **When** both accounts are reloaded via `GET /accounts/{id}`
- **Then** Bob's `balance` is `750.00`
- **And** Carol's `balance` is `1250.00`
