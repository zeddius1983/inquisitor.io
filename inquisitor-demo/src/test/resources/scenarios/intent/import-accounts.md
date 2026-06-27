# Import accounts from CSV and plain text

This scenario states the business intent and names no endpoints, paths, or request
bodies for the API calls — the harness has the application's OpenAPI description, so
the model must read it to work out which calls to make, including how to send each
body's content type and how to supply a default currency for rows that omit one. The
database steps keep their SQL, since the schema is not part of the OpenAPI description.

## Reset the database

Start from a clean slate so the final row count is exact, regardless of any accounts
other scenarios may have left behind.

```sql
TRUNCATE account, transaction RESTART IDENTITY CASCADE;
```

Both tables should be emptied.

## Import accounts from CSV

Bulk-import three accounts from CSV data: Alice in USD, Bob in EUR, and Carol with no
currency named. Carol's currency should fall back to a supplied default of GBP. The
import should succeed and return three accounts — Alice USD, Bob EUR, and Carol GBP.

## Import accounts from plain text

Bulk-import two more accounts from plain-text data, one owner per line: Dave and Erin.
Neither names a currency, so both should fall back to a supplied default of JPY. The
import should succeed and return two accounts, both in JPY.

## Verify the imported accounts with SQL

Confirm the imported accounts persisted with the right currencies.

```sql
SELECT owner, currency FROM account
WHERE owner IN ('Alice', 'Bob', 'Carol', 'Dave', 'Erin')
ORDER BY owner;
```

Exactly five rows should be returned: Alice USD, Bob EUR, Carol GBP, Dave JPY, Erin
JPY.
