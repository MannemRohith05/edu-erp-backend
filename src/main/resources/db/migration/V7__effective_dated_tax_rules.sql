-- Adds optional, effective-dated tax rules and immutable invoice tax snapshots.
CREATE TABLE tax_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    jurisdiction VARCHAR(80) NOT NULL,
    category_pattern VARCHAR(120) NOT NULL,
    rate DECIMAL(7,4) NOT NULL,
    price_includes_tax BOOLEAN NOT NULL DEFAULT FALSE,
    applies_to_fees BOOLEAN NOT NULL DEFAULT TRUE,
    applies_to_payroll BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_rules_code_from UNIQUE (code, valid_from),
    CONSTRAINT fk_tax_rules_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

ALTER TABLE fee_invoices ADD COLUMN subtotal DECIMAL(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE fee_invoices ADD COLUMN tax_total DECIMAL(12,2) NOT NULL DEFAULT 0.00;
UPDATE fee_invoices SET subtotal = amount WHERE subtotal = 0.00;

CREATE TABLE tax_breakdowns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    tax_rule_id BIGINT NOT NULL,
    rule_code VARCHAR(40) NOT NULL,
    rule_name VARCHAR(120) NOT NULL,
    jurisdiction VARCHAR(80) NOT NULL,
    rate DECIMAL(7,4) NOT NULL,
    taxable_amount DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL,
    price_includes_tax BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_breakdown_invoice_rule UNIQUE (invoice_id, tax_rule_id),
    CONSTRAINT fk_tax_breakdowns_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices (id),
    CONSTRAINT fk_tax_breakdowns_rule FOREIGN KEY (tax_rule_id) REFERENCES tax_rules (id)
);
