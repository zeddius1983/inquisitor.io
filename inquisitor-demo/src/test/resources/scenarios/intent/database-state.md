# Database state matches the API

This scenario states the business intent and names no endpoints, paths, or request
bodies for the API calls — the harness has the application's OpenAPI description, so
the model must read it to work out which calls to make. The database steps keep their
SQL, since the schema is not part of the OpenAPI description.

## Seed an account with SQL

Insert an account for "Frank" in EUR with a zero balance directly into the database,
returning its id.

```sql
INSERT INTO account (owner, currency, balance) VALUES ('Frank', 'EUR', 0) RETURNING id;
```

Exactly one row should be inserted and the new account `id` returned.

## Read the seeded account through the API

Look Frank's account up through the API to confirm the app sees the row that SQL
inserted. The response should succeed, showing owner Frank, currency EUR, and a
balance of 0.00.

## Deposit through the API

Deposit 75.00 into Frank's account through the API. The deposit should succeed and his
balance should then read 75.00.

## Verify the persisted transaction with SQL

Query the `transaction` table to confirm the deposit was recorded.

```sql
SELECT type, amount FROM transaction WHERE account_id = {frankId};
```

Exactly one row should be returned, with a `type` of `DEPOSIT` and an `amount` of
75.00.
