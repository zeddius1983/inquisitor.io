# Transfer funds between accounts seeded by a script

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

The scenario seeds its own data from the SQL script `seeded-accounts.sql`, which
creates two US-dollar accounts: Dave's, with id 900001, and Erin's, with id 900002,
each holding 500.00.

## Seed the accounts with the fixture script

Run the `seeded-accounts.sql` script, then look up Dave's and Erin's accounts and
confirm both exist with a balance of 500.00.

## Transfer funds

Move 150.00 from Dave's account to Erin's account. The transfer should succeed.

## Confirm the resulting balances

Look both accounts up again and confirm the money moved: Dave's balance should now be
350.00 and Erin's should be 650.00.
