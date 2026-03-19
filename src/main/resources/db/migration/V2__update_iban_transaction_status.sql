ALTER TABLE transaction_status_history
    ADD recorded_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE accounts
    ADD CONSTRAINT uc_accounts_iban UNIQUE (iban);

ALTER TABLE transaction_status_history
DROP
COLUMN changed_at;

ALTER TABLE accounts
    ALTER COLUMN iban SET NOT NULL;