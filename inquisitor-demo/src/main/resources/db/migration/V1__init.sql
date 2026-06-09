CREATE TABLE account (
    id         BIGSERIAL     PRIMARY KEY,
    owner      VARCHAR(100)  NOT NULL,
    currency   CHAR(3)       NOT NULL DEFAULT 'USD',
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE transaction (
    id         BIGSERIAL     PRIMARY KEY,
    account_id BIGINT        NOT NULL REFERENCES account(id),
    type       VARCHAR(20)   NOT NULL,
    amount     NUMERIC(19,4) NOT NULL,
    reference  VARCHAR(36),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_account_id ON transaction(account_id);
CREATE INDEX idx_transaction_created_at ON transaction(created_at DESC);
