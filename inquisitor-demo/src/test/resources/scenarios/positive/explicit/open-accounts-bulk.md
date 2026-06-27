# Open several accounts from a table

Exercises opening many accounts in one step and then funding them — parameterised
over a table of owners/currencies and a table of deposit ranges. One `POST /accounts`
per row, then one `POST /accounts/{id}/deposits` per row.

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

## Step 3 — Deposit into each account

**Intent:** Make a single deposit into each account opened in Step 1, choosing any
amount within that account's range.

| owner     | min | max  |
|-----------|-----|------|
| Jackie C  | 100 | 500  |
| Satoshi N | 1   | 1000 |
| Gordon R  | 20  | 60   |

```
POST /accounts/{id}/deposits
{ "amount": <any amount within the row's min-max range> }
```

**Expected response (for every row)**
- Status: `201 Created`
- `balance` equals the deposited amount, which lies within the row's `min`-`max`
  range (the accounts started empty, so the single deposit sets the balance)
