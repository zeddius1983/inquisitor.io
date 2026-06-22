# Open an account and make a deposit

Exercises the basic account lifecycle: open an account, deposit funds, and read
the account back to confirm the balance persisted.

## Step 1 — Open an account

**Intent:** Open a new account for "Alice" in USD.

```
POST /accounts
{ "owner": "Alice", "currency": "USD" }
```

**Expected response**
- Status: `201 Created`
- Body has a numeric `id`
- `balance` is `0.00`
- `currency` is `USD`

## Step 2 — Deposit funds

**Intent:** Deposit 500.00 into the account opened in Step 1.

```
POST /accounts/{id}/deposits
{ "amount": 500.00 }
```

**Expected response**
- Status: `201 Created`
- `balance` is `500.00`

## Step 3 — Read the account back

**Intent:** Reload the account to confirm the deposit persisted.

```
GET /accounts/{id}
```

**Expected response**
- Status: `200 OK`
- `balance` is `500.00`
- `currency` is `USD`
