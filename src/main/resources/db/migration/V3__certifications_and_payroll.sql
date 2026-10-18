-- Administrative certification issuance and teacher payroll.
-- This migration only creates new tables and preserves all existing data.

CREATE TABLE certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    verification_ref VARCHAR(48) NOT NULL,
    student_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    certificate_type VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    issue_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_by BIGINT NOT NULL,
    issued_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    notes VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_certificate_verification_ref UNIQUE (verification_ref),
    CONSTRAINT fk_certificates_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_certificates_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_certificates_issued_by FOREIGN KEY (issued_by) REFERENCES users (id)
);

CREATE INDEX ix_certificates_student ON certificates (student_id);
CREATE INDEX ix_certificates_status ON certificates (status);

CREATE TABLE salary_structures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    gross_salary DECIMAL(12,2) NOT NULL,
    allowances DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deductions DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_frequency VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    configured_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_salary_structures_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_salary_structures_configured_by FOREIGN KEY (configured_by) REFERENCES users (id)
);

CREATE INDEX ix_salary_structures_teacher_date ON salary_structures (teacher_id, effective_date);

CREATE TABLE payroll_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    salary_structure_id BIGINT NOT NULL,
    payroll_year INT NOT NULL,
    payroll_month INT NOT NULL,
    gross_salary DECIMAL(12,2) NOT NULL,
    allowances DECIMAL(12,2) NOT NULL,
    deductions DECIMAL(12,2) NOT NULL,
    net_salary DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_date DATE NULL,
    payment_reference VARCHAR(100) NULL,
    payslip_reference VARCHAR(255) NULL,
    last_modified_by BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payroll_teacher_period UNIQUE (teacher_id, payroll_year, payroll_month),
    CONSTRAINT fk_payroll_records_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_payroll_records_structure FOREIGN KEY (salary_structure_id) REFERENCES salary_structures (id),
    CONSTRAINT fk_payroll_records_modified_by FOREIGN KEY (last_modified_by) REFERENCES users (id)
);

CREATE INDEX ix_payroll_records_status ON payroll_records (status);
