# Transaction history reflects all operations in order

Every deposit and withdrawal must be recorded, and the transaction history must
be returned newest-first.

## Step 1 — Open an account

**Intent:** Open an account for "Eve" in USD.

```
POST /accounts
{ "owner": "Eve", "currency": "USD" }
```

**Expected response**
- Status: `201 Created` with a numeric `id`
- `balance` is `0.00`

## Step 2 — Record two deposits and a withdrawal

**Intent:** In order, deposit 200.00, deposit 50.00, then withdraw 30.00.

```
POST /accounts/{eveId}/deposits
{ "amount": 200.00 }

POST /accounts/{eveId}/deposits
{ "amount": 50.00 }

POST /accounts/{eveId}/withdrawals
{ "amount": 30.00 }
```

**Expected response**
- Each operation returns `201 Created`
- The final `balance` is `220.00`

## Step 3 — Retrieve the transaction history

**Intent:** Fetch the account's transactions.

```
GET /accounts/{eveId}/transactions
```

**Expected response**
- Status: `200 OK`
- Exactly **three** transactions
- They appear **newest-first**: the 30.00 withdrawal, then the 50.00 deposit,
  then the 200.00 deposit
