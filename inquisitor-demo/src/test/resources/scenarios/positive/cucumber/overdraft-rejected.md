# Overdraft attempt is rejected

Feature: a transfer larger than the source account's balance is rejected, and the
source balance is left untouched.

## Fund Dave's account

- **Given** a new USD account is opened for Dave via `POST /accounts`
- **When** `100.00` is deposited into it via `POST /accounts/{id}/deposits`
- **Then** the account is created with `201` and a numeric `id`
- **And** after the deposit his `balance` reads `100.00`

## Open a recipient account for Erin

- **When** a new USD account is opened for Erin via `POST /accounts`
- **Then** the account is created with `201` and a numeric `id`

## Attempt to overdraw

- **Given** Dave holds only `100.00`
- **When** a transfer of `500.00` is attempted from Dave to Erin via `POST /transfers`
- **Then** the request is rejected with `422 Unprocessable Entity`
- **And** the `Content-Type` is `application/problem+json`
- **And** the body is a valid RFC 9457 problem detail carrying at least a `title`
  and a `detail`

## Confirm no money moved

- **When** Dave's account is reloaded via `GET /accounts/{id}`
- **Then** the response is `200 OK`
- **And** his `balance` is still `100.00`
