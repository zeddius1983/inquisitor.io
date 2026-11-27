# Transfer funds between seeded accounts

Feature: money can be moved between accounts seeded by an SQL fixture, and both
balances reflect the transfer. The fixture seeds Dave (id `900001`) and Erin (id
`900002`) in USD with `500.00` each, once for the whole scenario.

## The seeded accounts are in place

- **Given** the seeded accounts `900001` (Dave) and `900002` (Erin)
- **When** both accounts are loaded
- **Then** each responds with `200 OK`
- **And** each `balance` reads `500.00`

## Transfer money from Dave to Erin

- **When** `200.00` is transferred from Dave's account to Erin's
- **Then** the response is `201 Created`
- **And** the returned summary references both account ids and an `amount` of `200.00`

## Confirm the resulting balances

- **When** both accounts are reloaded
- **Then** Dave's `balance` is `300.00`
- **And** Erin's `balance` is `700.00`
