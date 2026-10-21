-- Adds non-destructive provider metadata for verified Razorpay payments.
ALTER TABLE fee_payments ADD COLUMN provider VARCHAR(30) NULL;
ALTER TABLE fee_payments ADD COLUMN provider_order_id VARCHAR(120) NULL;
ALTER TABLE fee_payments ADD COLUMN provider_payment_id VARCHAR(120) NULL;
ALTER TABLE fee_payments ADD COLUMN provider_signature VARCHAR(255) NULL;
ALTER TABLE fee_payments ADD COLUMN failure_code VARCHAR(120) NULL;
ALTER TABLE fee_payments ADD COLUMN refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;
CREATE UNIQUE INDEX uk_fee_payment_provider_order ON fee_payments (provider_order_id);
CREATE UNIQUE INDEX uk_fee_payment_provider_payment ON fee_payments (provider_payment_id);
