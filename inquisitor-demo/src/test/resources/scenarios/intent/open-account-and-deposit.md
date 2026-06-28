# Open an account and make a deposit

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

## Open an account

Open a new bank account for a customer named "Alice", denominated in US dollars. It
should be created successfully and start with a zero balance.

## Deposit funds

Deposit 500.00 into Alice's newly opened account. The deposit should succeed and her
balance should then read 500.00.

## Confirm the balance

Look Alice's account up again and confirm its balance is 500.00 and its currency is
US dollars.
