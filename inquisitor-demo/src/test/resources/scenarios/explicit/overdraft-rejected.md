# Overdraft attempt is rejected

A transfer that exceeds the source account's balance must be rejected, and the
source balance must be left untouched.

## Step 1 — Open and fund Dave's account

**Intent:** Open an account for "Dave" in USD and deposit 100.00.

```
POST /accounts
{ "owner": "Dave", "currency": "USD" }

POST /accounts/{daveId}/deposits
{ "amount": 100.00 }
```

**Expected response**
- Account creation: `201 Created` with a numeric `id`
- After the deposit, `balance` is `100.00`

## Step 2 — Open a recipient account

**Intent:** Open a second account for "Erin" in USD to receive the transfer.

```
POST /accounts
{ "owner": "Erin", "currency": "USD" }
```

**Expected response**
- Status: `201 Created` with a numeric `id`

## Step 3 — Attempt an overdrawn transfer

**Intent:** Try to transfer 500.00 from Dave (balance 100.00) to Erin — more than
Dave has.

```
POST /transfers
{ "fromId": {daveId}, "toId": {erinId}, "amount": 500.00 }
```

**Expected response**
- Status: `422 Unprocessable Entity`
- Content-Type `application/problem+json`
- Body is a valid RFC 9457 problem detail (includes at least `title` and `detail`)

## Step 4 — Verify the balance is unchanged

**Intent:** Re-read Dave's account to confirm the failed transfer did not move money.

```
GET /accounts/{daveId}
```

**Expected response**
- Status: `200 OK`
- `balance` is still `100.00`
