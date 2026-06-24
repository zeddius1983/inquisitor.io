# Import accounts from CSV and plain text

Feature: bulk import accepts both CSV (`text/csv`) and plain-text (`text/plain`)
bodies, and an `X-Default-Currency` header supplies the currency for any row that
does not name its own.

## Reset the database

- **Given** other scenarios may have left accounts behind
- **When** both tables are truncated so the final row count is exact:

  ```sql
  TRUNCATE account, transaction RESTART IDENTITY CASCADE;
  ```

- **Then** both the `account` and `transaction` tables are empty

## Import accounts from CSV

- **When** three accounts are imported by POSTing a `text/csv` body to
  `/accounts/import` with an `X-Default-Currency` header of GBP:

  ```
  owner,currency
  Alice,USD
  Bob,EUR
  Carol,
  ```

- **Then** the response is `201 Created`
- **And** it lists three accounts: Alice in `USD`, Bob in `EUR`, and Carol in `GBP`
  (the header default, since her row left the currency blank)

## Import accounts from plain text

- **When** two more accounts are imported by POSTing a `text/plain` body to
  `/accounts/import` with an `X-Default-Currency` header of JPY, one owner per line:

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
