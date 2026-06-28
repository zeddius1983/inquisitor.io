# Transfer funds between two accounts

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

## Open and fund Bob's account

Open a new account for "Bob" in US dollars, then deposit 1000.00 into it. The account
should be created successfully and, after the deposit, its balance should read
1000.00.

## Open and fund Carol's account

Open a second account for "Carol" in US dollars and deposit 1000.00 into it. It too
should be created successfully and read 1000.00 after the deposit.

## Transfer funds

Move 250.00 from Bob's account to Carol's account. The transfer should succeed.

## Confirm the resulting balances

Look both accounts up again and confirm the money moved: Bob's balance should now be
750.00 and Carol's should be 1250.00.
