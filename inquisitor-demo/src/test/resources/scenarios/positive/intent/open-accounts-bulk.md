# Open several accounts from a table

States only the business intent and names no endpoints, paths, or request bodies —
the harness has the application's OpenAPI description, so the model must read it to
work out how to open an account. The accounts to open are given as a table, one per
row, in the style of a parameterised test.

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
