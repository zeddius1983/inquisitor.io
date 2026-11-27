# Transfer funds between accounts seeded by a script

Feature: a scenario can seed its own data from an SQL script file. The script
`seeded-accounts.sql` creates Dave (id `900001`) and Erin (id `900002`) in USD with
`500.00` each.

## Seed the accounts with the fixture script

- **Given** the SQL script `seeded-accounts.sql` has been run
- **When** accounts `900001` (Dave) and `900002` (Erin) are loaded
- **Then** each responds with `200 OK`
- **And** each `balance` reads `500.00`

## Transfer money from Dave to Erin

- **When** `150.00` is transferred from Dave's account to Erin's
- **Then** the response is `201 Created`
- **And** the returned summary references both account ids and an `amount` of `150.00`

## Confirm the resulting balances

- **When** both accounts are reloaded
- **Then** Dave's `balance` is `350.00`
- **And** Erin's `balance` is `650.00`
