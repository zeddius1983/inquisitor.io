# Open several accounts from a table

States only the business intent and names no endpoints, paths, or request bodies —
the harness has the application's OpenAPI description, so the model must read it to
work out which calls to make. The accounts to open, and the amounts to deposit, are
each given as a table, one row per account, in the style of a parameterised test.

## Open every account in the table

Open a new bank account for each row below. Each account should be created
successfully and start with a zero balance, denominated in the row's currency.

| owner     | currency |
|-----------|----------|
| Jackie C  | CNY      |
| Satoshi N | JPY      |
| Gordon R  | GBP      |

All three should be created.

## Confirm every account exists

Look each owner from the table up again and confirm an account exists for them with
the stated currency and a zero balance — Jackie C in CNY, Satoshi N in JPY, and
Gordon R in GBP.

## Deposit into each account

Make a single deposit into each account opened above, choosing any amount within the
range given for that account. Each deposit should succeed, and afterwards the
account's balance should equal the amount deposited — which must fall within the
stated range (the accounts started empty).

| owner     | deposit (min-max) |
|-----------|-------------------|
| Jackie C  | 100-500           |
| Satoshi N | 1-1000            |
| Gordon R  | 20-60             |
