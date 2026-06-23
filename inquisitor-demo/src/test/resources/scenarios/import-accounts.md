# Import accounts from CSV and plain text

Exercises bulk account import over different request content types and a custom
header. `POST /accounts/import` accepts either CSV (`text/csv`) or plain text
(`text/plain`), and an `X-Default-Currency` header supplies the currency for any
row that doesn't name its own.

## Step 1 — Reset the database

**Intent:** Start from a clean slate so the row count in the final step is exact,
regardless of any accounts other scenarios may have left behind.

```sql
TRUNCATE account, transaction RESTART IDENTITY CASCADE;
```

**Expected response**
- The statement succeeds and both tables are emptied

## Step 2 — Import accounts from CSV

**Intent:** Import three accounts from a CSV body. The first two name their own
currency; the third (Carol) leaves it blank, so it must fall back to the
`X-Default-Currency` header.

```
POST /accounts/import
Content-Type: text/csv
X-Default-Currency: GBP

owner,currency
Alice,USD
Bob,EUR
Carol,
```

**Expected response**
- Status: `201 Created`
- The response is a list of three accounts
- The accounts are Alice with `currency` `USD`, Bob with `EUR`, and Carol with
  `GBP` (the header default)

## Step 3 — Import accounts from plain text

**Intent:** Import two more accounts from a plain-text body, one owner per line.
Neither line names a currency, so both must use the `X-Default-Currency` header.

```
POST /accounts/import
Content-Type: text/plain
X-Default-Currency: JPY

Dave
Erin
```

**Expected response**
- Status: `201 Created`
- The response is a list of two accounts
- Both Dave and Erin have `currency` `JPY`

## Step 4 — Verify the imported accounts with SQL

**Intent:** Confirm the imported accounts persisted with the right currencies.

```sql
SELECT owner, currency FROM account
WHERE owner IN ('Alice', 'Bob', 'Carol', 'Dave', 'Erin')
ORDER BY owner;
```

**Expected response**
- Exactly five rows
- Alice `USD`, Bob `EUR`, Carol `GBP`, Dave `JPY`, Erin `JPY`
