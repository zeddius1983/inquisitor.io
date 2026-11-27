# Transfer funds between accounts seeded by a script

The scenario seeds its own data: step 1 runs the `seeded-accounts.sql` script, which
creates Dave (id `900001`) and Erin (id `900002`), both in USD with a balance of 500.00.
Then money moves between them and both balances must reflect the transfer.

## Step 1 — Seed the accounts with the fixture script

**Intent:** Run the SQL script `seeded-accounts.sql`, then read both accounts back
through the API to confirm the seed.

```
GET /accounts/900001
GET /accounts/900002
```

**Expected response**
- The script runs without an error
- Both requests return `200 OK`
- Dave's (`900001`) and Erin's (`900002`) `balance` is `500.00`

## Step 2 — Transfer funds

**Intent:** Transfer 150.00 from Dave's account to Erin's account.

```
POST /transfers
{ "fromId": 900001, "toId": 900002, "amount": 150.00 }
```

**Expected response**
- Status: `201 Created`
- The summary references both account ids and an `amount` of `150.00`

## Step 3 — Verify resulting balances

**Intent:** Re-read both accounts to confirm the transfer settled.

```
GET /accounts/900001
GET /accounts/900002
```

**Expected response**
- Dave's `balance` is `350.00`
- Erin's `balance` is `650.00`
