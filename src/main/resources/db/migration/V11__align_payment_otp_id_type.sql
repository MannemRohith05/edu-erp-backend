-- Align the database UUID text column with Hibernate's String mapping.
-- This is a forward-only, data-preserving type change; no rows are deleted.
ALTER TABLE payment_otp_challenges MODIFY id VARCHAR(36) NOT NULL;
