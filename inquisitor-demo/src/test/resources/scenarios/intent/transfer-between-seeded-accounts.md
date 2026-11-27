# Transfer funds between seeded accounts

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

Before the scenario starts, an SQL fixture has seeded two US-dollar accounts: Dave's,
with id 900001, and Erin's, with id 900002, each holding 500.00.

## Confirm the seeded accounts

Look up Dave's and Erin's accounts and confirm both exist with a balance of 500.00.

## Transfer funds

Move 200.00 from Dave's account to Erin's account. The transfer should succeed.

## Confirm the resulting balances

Look both accounts up again and confirm the money moved: Dave's balance should now be
300.00 and Erin's should be 700.00.
