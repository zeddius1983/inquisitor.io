# Transaction history reflects all operations in order

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

## Open an account

Open a new account for "Eve" in US dollars. It should be created successfully and
start with a zero balance.

## Record two deposits and a withdrawal

On Eve's account, in this exact order: deposit 200.00, then deposit 50.00, then
withdraw 30.00. Each operation should succeed, and her balance should settle at
220.00.

## Retrieve the transaction history

Fetch Eve's transaction history. It should list exactly three transactions, ordered
newest-first: the 30.00 withdrawal, then the 50.00 deposit, then the 200.00 deposit.
