# Overdraft attempt is rejected

Open an account for "Dave" and deposit 100.00 via POST /accounts/{id}/deposits.
Attempt to transfer 500.00 from Dave's account to any other account via POST /transfers.
Verify the response status is 422 and the body is a valid RFC 9457 problem detail.
Verify Dave's balance is still 100.00 after the failed transfer.
