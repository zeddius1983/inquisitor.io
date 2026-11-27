-- Fixture for the transfer-between-seeded-accounts scenario: two funded USD accounts
-- with fixed ids. Applied once before the scenario (not before each step) via @Sql.
-- Clears its own rows first so it is idempotent on a reused Testcontainer.
DELETE FROM transaction WHERE account_id IN (900001, 900002);
DELETE FROM account WHERE id IN (900001, 900002);

INSERT INTO account (id, owner, currency, balance) VALUES
    (900001, 'Dave', 'USD', 500.00),
    (900002, 'Erin', 'USD', 500.00);
