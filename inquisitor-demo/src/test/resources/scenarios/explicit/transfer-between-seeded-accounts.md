# Transfer funds between seeded accounts

Two funded accounts are seeded by an SQL fixture before the scenario starts: Dave
(id `900001`) and Erin (id `900002`), both in USD with a balance of 500.00. Move money
between them and confirm both balances reflect the transfer. The fixture runs once for
the whole scenario, so state built up in earlier steps must survive into later ones.

## Step 1 — Confirm the seeded accounts

**Intent:** Read both seeded accounts to confirm the fixture was applied.

```
GET /accounts/900001
GET /accounts/900002
```

**Expected response**
- Both requests return `200 OK`
- Dave's (`900001`) `balance` is `500.00`
- Erin's (`900002`) `balance` is `500.00`

## Step 2 — Transfer funds

**Intent:** Transfer 200.00 from Dave's account to Erin's account.

```
POST /transfers
{ "fromId": 900001, "toId": 900002, "amount": 200.00 }
```

**Expected response**
- Status: `201 Created`
- The summary references both account ids and an `amount` of `200.00`

## Step 3 — Verify resulting balances

**Intent:** Re-read both accounts to confirm the transfer settled.

```
GET /accounts/900001
GET /accounts/900002
```

**Expected response**
- Dave's `balance` is `300.00`
- Erin's `balance` is `700.00`
