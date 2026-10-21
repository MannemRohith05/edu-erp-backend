-- Adds assessment workflow metadata without rewriting existing grades.
CREATE TABLE assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    max_marks DECIMAL(8,2) NOT NULL,
    weightage DECIMAL(6,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    submitted_at DATETIME NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_course_title UNIQUE (course_id, title),
    CONSTRAINT fk_assessments_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_assessments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_assessments_approved_by FOREIGN KEY (approved_by) REFERENCES users (id)
);
ALTER TABLE grades ADD COLUMN assessment_id BIGINT NULL;
ALTER TABLE grades ADD COLUMN workflow_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE grades ADD COLUMN submitted_at DATETIME NULL;
ALTER TABLE grades ADD COLUMN approved_at DATETIME NULL;
ALTER TABLE grades ADD COLUMN approved_by BIGINT NULL;
ALTER TABLE grades ADD CONSTRAINT fk_grades_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id);
ALTER TABLE grades ADD CONSTRAINT fk_grades_approved_by FOREIGN KEY (approved_by) REFERENCES users (id);
CREATE TABLE grade_correction_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grade_id BIGINT NOT NULL,
    changed_by BIGINT NOT NULL,
    old_marks DECIMAL(10,2) NOT NULL,
    new_marks DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grade_corrections_grade FOREIGN KEY (grade_id) REFERENCES grades (id),
    CONSTRAINT fk_grade_corrections_user FOREIGN KEY (changed_by) REFERENCES users (id)
);
