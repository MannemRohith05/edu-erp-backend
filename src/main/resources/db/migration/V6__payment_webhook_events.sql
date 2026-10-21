-- Durable webhook deduplication. Existing payment data is preserved.
CREATE TABLE payment_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(30) NOT NULL,
    event_id VARCHAR(160) NOT NULL,
    received_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_provider_event UNIQUE (provider, event_id)
);
