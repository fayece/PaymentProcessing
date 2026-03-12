CREATE TABLE accounts
(
    id         UUID   NOT NULL,
    name       VARCHAR(255),
    iban       VARCHAR(255),
    balance    DECIMAL,
    status     VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE,
    version    BIGINT NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id)
);

CREATE TABLE fraud_flags
(
    id             UUID NOT NULL,
    transaction_id UUID,
    rule_name      VARCHAR(255),
    reason         VARCHAR(255),
    flagged_at     TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_fraud_flags PRIMARY KEY (id)
);

CREATE TABLE idempotency_keys
(
    key               VARCHAR(255) NOT NULL,
    transaction_id    UUID,
    response_snapshot VARCHAR(255),
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (key)
);

CREATE TABLE transaction_status_history
(
    id             UUID NOT NULL,
    transaction_id UUID,
    status         VARCHAR(255),
    reason         VARCHAR(255),
    changed_at     TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_transaction_status_history PRIMARY KEY (id)
);

CREATE TABLE transactions
(
    id                     UUID   NOT NULL,
    source_account_id      UUID,
    destination_account_id UUID,
    amount                 DECIMAL,
    currency               VARCHAR(255),
    status                 VARCHAR(255),
    created_at             TIMESTAMP WITHOUT TIME ZONE,
    updated_at             TIMESTAMP WITHOUT TIME ZONE,
    version                BIGINT NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id)
);

ALTER TABLE fraud_flags
    ADD CONSTRAINT FK_FRAUD_FLAGS_ON_TRANSACTION FOREIGN KEY (transaction_id) REFERENCES transactions (id);

ALTER TABLE transactions
    ADD CONSTRAINT FK_TRANSACTIONS_ON_DESTINATIONACCOUNT FOREIGN KEY (destination_account_id) REFERENCES accounts (id);

ALTER TABLE transactions
    ADD CONSTRAINT FK_TRANSACTIONS_ON_SOURCEACCOUNT FOREIGN KEY (source_account_id) REFERENCES accounts (id);

ALTER TABLE transaction_status_history
    ADD CONSTRAINT FK_TRANSACTION_STATUS_HISTORY_ON_TRANSACTION FOREIGN KEY (transaction_id) REFERENCES transactions (id);