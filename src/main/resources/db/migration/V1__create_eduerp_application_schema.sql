-- EduERP initial schema.
-- Target: the database selected by the JDBC connection (normally edu_erp).
-- This migration never touches connection_test and contains no data inserts.

DELIMITER $$

CREATE PROCEDURE __eduerp_v1_preflight()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'users')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'students')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'teachers')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'courses')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'course_enrollments')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'schedules')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'attendance')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'grades')
       OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'messages') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'EduERP V1 stopped: one or more application table names already exist; no application tables were created by this migration.';
    END IF;
END$$

CALL __eduerp_v1_preflight()$$
DROP PROCEDURE __eduerp_v1_preflight$$

DELIMITER ;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BIT(1) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    enrollment_date DATE,
    department VARCHAR(255),
    semester INT,
    parent_contact VARCHAR(255),
    address VARCHAR(255),
    CONSTRAINT pk_students PRIMARY KEY (id),
    CONSTRAINT uk_students_user_id UNIQUE (user_id),
    CONSTRAINT uk_students_student_id UNIQUE (student_id),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teachers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    department VARCHAR(255),
    qualification VARCHAR(255),
    specialization VARCHAR(255),
    joining_date DATE,
    CONSTRAINT pk_teachers PRIMARY KEY (id),
    CONSTRAINT uk_teachers_user_id UNIQUE (user_id),
    CONSTRAINT uk_teachers_employee_id UNIQUE (employee_id),
    CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_code VARCHAR(255) NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    description TEXT,
    credits INT,
    semester INT,
    department VARCHAR(255),
    teacher_id BIGINT,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uk_courses_course_code UNIQUE (course_code),
    CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_enrollments (
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    CONSTRAINT pk_course_enrollments PRIMARY KEY (course_id, student_id),
    CONSTRAINT fk_course_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_course_enrollments_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    day_of_week VARCHAR(255) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room VARCHAR(255),
    building VARCHAR(255),
    CONSTRAINT pk_schedules PRIMARY KEY (id),
    CONSTRAINT fk_schedules_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    date DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    marked_by BIGINT,
    marked_at DATETIME(6),
    remarks VARCHAR(255),
    CONSTRAINT pk_attendance PRIMARY KEY (id),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_attendance_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_attendance_marker FOREIGN KEY (marked_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE grades (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    assignment_name VARCHAR(255) NOT NULL,
    marks DOUBLE NOT NULL,
    total_marks DOUBLE NOT NULL,
    grade_type VARCHAR(255),
    letter_grade VARCHAR(255),
    graded_by BIGINT,
    graded_at DATETIME(6),
    feedback VARCHAR(255),
    CONSTRAINT pk_grades PRIMARY KEY (id),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_grades_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_grades_grader FOREIGN KEY (graded_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    sent_at DATETIME(6),
    read_at DATETIME(6),
    is_read BIT(1) NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
