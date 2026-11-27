# Open an account in a given currency

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

It runs once per set of parameters: an `owner`, a `currency` and a `deposit` amount,
given with every step.

## Open an account

Open a new account for the owner in the given currency. It should be created
successfully, belong to that owner, be held in that currency, and start with a zero
balance.

## Deposit funds

Deposit the given amount into the new account. The deposit should succeed and the
balance should then equal the amount deposited.

## Read the account back

Look the account up again and confirm the deposit persisted: the balance should equal
the deposited amount, still in the given currency.
