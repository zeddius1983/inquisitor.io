# Open an account in a given currency

A parameterized scenario: it runs once per set of parameters, which are given to you
with every step as `owner`, `currency` and `deposit`. Open an account for the owner in
the currency, deposit the amount, and confirm the account reflects all three.

## Step 1 — Open an account

**Intent:** Open a new account for this run's `owner` in this run's `currency`.

```
POST /accounts
{ "owner": "{owner}", "currency": "{currency}" }
```

**Expected response**
- Status: `201 Created`
- Body has a numeric `id`
- `owner` is this run's `owner`
- `currency` is this run's `currency`
- `balance` is `0.00`

## Step 2 — Deposit funds

**Intent:** Deposit this run's `deposit` amount into the account opened in Step 1.

```
POST /accounts/{id}/deposits
{ "amount": {deposit} }
```

**Expected response**
- Status: `201 Created`
- `balance` equals this run's `deposit`

## Step 3 — Read the account back

**Intent:** Reload the account to confirm the deposit persisted in the right currency.

```
GET /accounts/{id}
```

**Expected response**
- Status: `200 OK`
- `balance` equals this run's `deposit`
- `currency` is this run's `currency`
