# Import accounts from CSV and plain text

Feature: bulk import accepts both CSV and plain-text bodies, and a row that does
not name its own currency falls back to a supplied default.

## Reset the database

- **Given** other scenarios may have left accounts behind
- **When** both tables are truncated so the final row count is exact:

  ```sql
  TRUNCATE account, transaction RESTART IDENTITY CASCADE;
  ```

- **Then** both the `account` and `transaction` tables are empty

## Import accounts from CSV

- **When** three accounts are imported from this CSV data, with a default currency
  of GBP for any row that leaves the currency blank:

  ```
  owner,currency
  Alice,USD
  Bob,EUR
  Carol,
  ```

- **Then** the response is `201 Created`
- **And** it lists three accounts: Alice in `USD`, Bob in `EUR`, and Carol in `GBP`
  (the default, since her row left the currency blank)

## Import accounts from plain text

- **When** two more accounts are imported from this plain-text data, one owner per
  line, with a default currency of JPY:

  ```
  Dave
  Erin
  ```

- **Then** the response is `201 Created`
- **And** it lists two accounts, Dave and Erin, both in `JPY`

## Verify the imported accounts with SQL

- **When** the `account` table is queried for the five owners:

  ```sql
  SELECT owner, currency FROM account
  WHERE owner IN ('Alice', 'Bob', 'Carol', 'Dave', 'Erin')
  ORDER BY owner;
  ```

- **Then** exactly five rows are returned
- **And** they are Alice `USD`, Bob `EUR`, Carol `GBP`, Dave `JPY`, Erin `JPY`
