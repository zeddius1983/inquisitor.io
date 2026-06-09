# Open an account and make a deposit

Open a new account for owner "Alice" with currency USD via POST /accounts.
Deposit 500.00 into the new account via POST /accounts/{id}/deposits.
Retrieve the account via GET /accounts/{id} and verify the balance is 500.00.
