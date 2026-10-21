-- Forward-only payroll and assessment metadata. Existing values are preserved.
ALTER TABLE salary_structures ADD COLUMN basic_salary DECIMAL(12,2) NULL;
ALTER TABLE salary_structures ADD COLUMN bonuses DECIMAL(12,2) NULL;
UPDATE salary_structures SET basic_salary = gross_salary, bonuses = 0.00 WHERE basic_salary IS NULL;
ALTER TABLE salary_structures MODIFY basic_salary DECIMAL(12,2) NOT NULL;
ALTER TABLE salary_structures MODIFY bonuses DECIMAL(12,2) NOT NULL DEFAULT 0.00;

ALTER TABLE payroll_records ADD COLUMN basic_salary DECIMAL(12,2) NULL;
ALTER TABLE payroll_records ADD COLUMN bonuses DECIMAL(12,2) NULL;
ALTER TABLE payroll_records ADD COLUMN payment_method VARCHAR(30) NULL;
ALTER TABLE payroll_records ADD COLUMN payment_notes VARCHAR(500) NULL;
UPDATE payroll_records SET basic_salary = gross_salary, bonuses = 0.00 WHERE basic_salary IS NULL;
ALTER TABLE payroll_records MODIFY basic_salary DECIMAL(12,2) NOT NULL;
ALTER TABLE payroll_records MODIFY bonuses DECIMAL(12,2) NOT NULL DEFAULT 0.00;

ALTER TABLE assessments ADD COLUMN assessment_type VARCHAR(30) NULL;
ALTER TABLE assessments ADD COLUMN academic_year VARCHAR(20) NULL;
ALTER TABLE assessments ADD COLUMN semester INT NULL;
ALTER TABLE assessments ADD COLUMN deadline DATE NULL;

CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80) NOT NULL,
    details VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    INDEX ix_audit_events_entity (entity_type, entity_id, created_at),
    INDEX ix_audit_events_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
