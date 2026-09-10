ALTER TABLE student
    ADD COLUMN parent_link_code_hash VARCHAR(64),
    ADD COLUMN parent_link_code_created_at TIMESTAMP;

CREATE UNIQUE INDEX uq_student_parent_link_code_hash
    ON student(parent_link_code_hash)
    WHERE parent_link_code_hash IS NOT NULL;

ALTER TABLE registration_request
    ADD COLUMN requested_student_id BIGINT REFERENCES student(student_id);

CREATE INDEX idx_registration_request_student_id
    ON registration_request(requested_student_id);

ALTER TABLE registration_request
    DROP CONSTRAINT chk_registration_requested_data;

ALTER TABLE registration_request
    ADD CONSTRAINT chk_registration_requested_data CHECK (
        (requested_role = 'STUDENT'
            AND requested_class_id IS NOT NULL
            AND requested_subject_id IS NULL
            AND requested_student_id IS NULL)
        OR (requested_role = 'TEACHER'
            AND requested_subject_id IS NOT NULL
            AND requested_class_id IS NULL
            AND requested_student_id IS NULL)
        OR (requested_role = 'PARENT'
            AND requested_student_id IS NOT NULL
            AND requested_class_id IS NULL
            AND requested_subject_id IS NULL)
        -- Preserve old rows created before parent-to-student codes were introduced.
        OR (requested_role = 'PARENT'
            AND requested_student_id IS NULL
            AND requested_class_id IS NOT NULL
            AND requested_subject_id IS NULL)
    );
