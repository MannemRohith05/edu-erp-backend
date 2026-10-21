ALTER TABLE fee_payments ADD COLUMN mobile_number VARCHAR(20) NULL;

CREATE TABLE payment_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_notification_payment FOREIGN KEY (payment_id) REFERENCES fee_payments (id),
    CONSTRAINT uk_payment_notification_delivery UNIQUE (payment_id, channel, recipient)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
