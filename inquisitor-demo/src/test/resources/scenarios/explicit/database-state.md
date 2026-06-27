# Database state matches the API

Exercises the SQL tool alongside HTTP: seed an account directly in the database,
read it back through the API, then make a deposit via the API and verify the
persisted transaction row with SQL.

## Step 1 — Seed an account with SQL

**Intent:** Insert an account for "Frank" in EUR directly into the database.

```sql
INSERT INTO account (owner, currency, balance) VALUES ('Frank', 'EUR', 0) RETURNING id;
```

**Expected response**
- One row inserted
- The statement returns the new account `id`

## Step 2 — Read the seeded account through the API

**Intent:** Fetch the account via the API to confirm the app sees the row that
SQL inserted.

```
GET /accounts/{frankId}
```

**Expected response**
- Status: `200 OK`
- `owner` is `Frank`
- `currency` is `EUR`
- `balance` is `0.00`

## Step 3 — Deposit through the API

**Intent:** Deposit 75.00 into Frank's account.

```
POST /accounts/{frankId}/deposits
{ "amount": 75.00 }
```

**Expected response**
- Status: `201 Created`
- `balance` is `75.00`

## Step 4 — Verify the persisted transaction with SQL

**Intent:** Query the `transaction` table to confirm the deposit was recorded.

```sql
SELECT type, amount FROM transaction WHERE account_id = {frankId};
```

**Expected response**
- Exactly one row
- `type` is `DEPOSIT`
- `amount` is `75.00`
