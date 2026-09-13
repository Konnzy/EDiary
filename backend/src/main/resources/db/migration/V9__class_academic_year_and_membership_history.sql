ALTER TABLE school_class
    ADD COLUMN class_academic_year VARCHAR(9);

UPDATE school_class
SET class_academic_year = '2026/2027'
WHERE class_academic_year IS NULL;

ALTER TABLE school_class
    ALTER COLUMN class_academic_year SET NOT NULL,
    ADD CONSTRAINT fk_school_class_academic_year
        FOREIGN KEY (class_academic_year) REFERENCES academic_year(academic_year_value),
    DROP CONSTRAINT unique_class_number_letter,
    DROP CONSTRAINT IF EXISTS school_class_class_teacher_id_key,
    ADD CONSTRAINT uq_school_class_year_name
        UNIQUE (class_academic_year, class_number, class_letter),
    ADD CONSTRAINT uq_school_class_year_teacher
        UNIQUE (class_academic_year, class_teacher_id);

CREATE INDEX idx_school_class_academic_year
    ON school_class(class_academic_year);

CREATE TABLE student_class_membership (
    membership_id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student(student_id),
    class_id BIGINT NOT NULL REFERENCES school_class(class_id),
    membership_active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP,
    CONSTRAINT uq_student_class_membership UNIQUE (student_id, class_id),
    CONSTRAINT chk_student_class_membership_dates CHECK (
        (membership_active AND left_at IS NULL)
        OR (NOT membership_active AND left_at IS NOT NULL)
    )
);

INSERT INTO student_class_membership (student_id, class_id)
SELECT student_id, class_id
FROM student
WHERE class_id IS NOT NULL;

CREATE INDEX idx_membership_class_active
    ON student_class_membership(class_id, membership_active);

CREATE INDEX idx_membership_student_active
    ON student_class_membership(student_id, membership_active);
