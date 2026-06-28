# Transfer funds between two accounts

Open and fund two accounts, move money from one to the other, and confirm both
balances reflect the transfer.

## Step 1 — Open and fund Bob's account

**Intent:** Open an account for "Bob" in USD and deposit 1000.00.

```
POST /accounts
{ "owner": "Bob", "currency": "USD" }

POST /accounts/{bobId}/deposits
{ "amount": 1000.00 }
```

**Expected response**
- Account creation: `201 Created` with a numeric `id`
- After the deposit, `balance` is `1000.00`

## Step 2 — Open and fund Carol's account

**Intent:** Open an account for "Carol" in USD and deposit 1000.00.

```
POST /accounts
{ "owner": "Carol", "currency": "USD" }

POST /accounts/{carolId}/deposits
{ "amount": 1000.00 }
```

**Expected response**
- Account creation: `201 Created` with a numeric `id`
- After the deposit, `balance` is `1000.00`

## Step 3 — Transfer funds

**Intent:** Transfer 250.00 from Bob's account to Carol's account.

```
POST /transfers
{ "fromId": {bobId}, "toId": {carolId}, "amount": 250.00 }
```

**Expected response**
- Status: `201 Created`
- The summary references Bob's and Carol's account ids and an `amount` of `250.00`

## Step 4 — Verify resulting balances

**Intent:** Re-read both accounts to confirm the transfer settled.

```
GET /accounts/{bobId}
GET /accounts/{carolId}
```

**Expected response**
- Bob's `balance` is `750.00`
- Carol's `balance` is `1250.00`
