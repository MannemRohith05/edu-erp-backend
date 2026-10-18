-- Adds non-destructive fee payment state and idempotency controls.
ALTER TABLE fee_structures ADD COLUMN allow_partial BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE fee_payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE fee_payments ADD COLUMN idempotency_key VARCHAR(120) NULL;
ALTER TABLE fee_payments ADD COLUMN confirmed_at DATETIME NULL;
UPDATE fee_payments SET idempotency_key = CONCAT('legacy-', id) WHERE idempotency_key IS NULL;
ALTER TABLE fee_payments MODIFY idempotency_key VARCHAR(120) NOT NULL;
ALTER TABLE fee_payments ADD CONSTRAINT uk_fee_payment_idempotency UNIQUE (idempotency_key);
