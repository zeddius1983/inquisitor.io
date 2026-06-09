# Transaction history reflects all operations in order

Open an account for "Eve" and perform three operations: deposit 200.00, deposit 50.00, withdraw 30.00.
Retrieve the transaction list via GET /accounts/{id}/transactions.
Verify there are exactly three transactions and they appear newest-first.
Verify the final account balance is 220.00.
