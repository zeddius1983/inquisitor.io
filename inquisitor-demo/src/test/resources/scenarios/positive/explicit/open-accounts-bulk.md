# Open several accounts from a table

Exercises opening many accounts in one step, parameterised over a table of owners
and currencies — one `POST /accounts` per row.

## Step 1 — Open every account in the table

**Intent:** Open one account for each row below, sending the owner and currency from
that row.

| owner     | currency |
|-----------|----------|
| Jackie C  | CNY      |
| Satoshi N | JPY      |
| Gordon R  | GBP      |

```
POST /accounts
{ "owner": "<owner>", "currency": "<currency>" }
```

**Expected response (for every row)**
- Status: `201 Created`
- Body has a numeric `id`
- `balance` is `0.00`
- `currency` matches the row

## Step 2 — Confirm every account exists

**Intent:** Look each owner up again and confirm the account persisted with the
right currency and a zero balance.

```
GET /accounts?owner=<owner>
```

**Expected response (for every row)**
- Status: `200 OK`
- Exactly one account is returned for the owner
- Its `currency` matches the row and its `balance` is `0.00`
