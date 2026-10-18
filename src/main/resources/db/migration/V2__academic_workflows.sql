-- Backward-compatible academic workflow additions.
-- No existing records are deleted or rewritten.

ALTER TABLE grades
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE fee_structures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(80) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    due_date DATE NOT NULL,
    semester INT NULL,
    academic_year VARCHAR(20) NULL,
    course_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_fee_structures_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_fee_structures_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE fee_invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    fee_structure_id BIGINT NULL,
    category VARCHAR(80) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    adjustment DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    due_date DATE NOT NULL,
    adjustment_reason VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fee_invoice_student_structure UNIQUE (student_id, fee_structure_id),
    CONSTRAINT fk_fee_invoices_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_fee_invoices_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures (id),
    CONSTRAINT fk_fee_invoices_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE fee_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reference VARCHAR(100) NOT NULL,
    recorded_by BIGINT NOT NULL,
    paid_at DATETIME NOT NULL,
    notes VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fee_payment_reference UNIQUE (reference),
    CONSTRAINT fk_fee_payments_invoice FOREIGN KEY (invoice_id) REFERENCES fee_invoices (id),
    CONSTRAINT fk_fee_payments_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id)
);

CREATE INDEX ix_fee_invoices_student ON fee_invoices (student_id);
CREATE INDEX ix_fee_payments_invoice ON fee_payments (invoice_id);
