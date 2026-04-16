CREATE TABLE transactions.transactions (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                           reference VARCHAR(20) NOT NULL UNIQUE,
                                           idempotency_key VARCHAR(100) NOT NULL UNIQUE,
                                           type VARCHAR(20) NOT NULL,
                                           status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                           amount NUMERIC(19, 4) NOT NULL,
                                           currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
                                           source_account_number VARCHAR(10),
                                           destination_account_number VARCHAR(10),
                                           description VARCHAR(255),
                                           failure_reason VARCHAR(255),
                                           initiated_by UUID NOT NULL,
                                           created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                           updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions.ledger_entries (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             transaction_id UUID NOT NULL REFERENCES transactions.transactions(id),
                                             account_number VARCHAR(10) NOT NULL,
                                             entry_type VARCHAR(10) NOT NULL,
                                             amount NUMERIC(19, 4) NOT NULL,
                                             balance_before NUMERIC(19, 4) NOT NULL,
                                             balance_after NUMERIC(19, 4) NOT NULL,
                                             created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE SEQUENCE transactions.transaction_reference_seq
    START WITH 100000
    INCREMENT BY 1
    NO CYCLE;

CREATE INDEX idx_transactions_reference
    ON transactions.transactions(reference);

CREATE INDEX idx_transactions_source_account
    ON transactions.transactions(source_account_number);

CREATE INDEX idx_transactions_destination_account
    ON transactions.transactions(destination_account_number);

CREATE INDEX idx_transactions_initiated_by
    ON transactions.transactions(initiated_by);

CREATE INDEX idx_ledger_transaction_id
    ON transactions.ledger_entries(transaction_id);

CREATE INDEX idx_ledger_account_number
    ON transactions.ledger_entries(account_number);