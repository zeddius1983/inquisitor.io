# Overdraft attempt is rejected

This scenario states only the business intent — it names no endpoints, paths, or
request bodies. The harness has been given the application's OpenAPI description, so
the model must read it to work out which calls to make.

## Open and fund Dave's account

Open a new account for "Dave" in US dollars and deposit 100.00 into it. The account
should be created successfully and read 100.00 after the deposit.

## Open a recipient account

Open a second account for "Erin" in US dollars to receive a transfer. It should be
created successfully.

## Attempt an overdrawn transfer

Try to transfer 500.00 from Dave to Erin — more than Dave's 100.00 balance. The
transfer should be rejected as unprocessable: a `422` response whose body is a
well-formed RFC 9457 problem detail (carrying at least a `title` and a `detail`)
served as `application/problem+json`.

## Confirm no money moved

Look Dave's account up again and confirm the failed transfer left his balance
untouched at 100.00.
