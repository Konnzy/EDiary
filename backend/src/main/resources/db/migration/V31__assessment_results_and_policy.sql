ALTER TABLE mark ALTER COLUMN mark_score DROP NOT NULL;
ALTER TABLE mark ADD COLUMN mark_result VARCHAR(16);
ALTER TABLE mark ADD CONSTRAINT check_mark_result CHECK (
    (mark_score IS NOT NULL AND mark_result IS NULL) OR
    (mark_score IS NULL AND mark_result IS NOT NULL AND mark_result IN ('PASSED','NOT_PASSED','EXEMPTED'))
);

CREATE TABLE assessment_policy (
    class_id BIGINT NOT NULL REFERENCES school_class(class_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subject(subject_id) ON DELETE CASCADE,
    mode VARCHAR(16) NOT NULL DEFAULT 'NUMERIC' CHECK (mode IN ('NUMERIC','PASS_FAIL')),
    allow_passed BOOLEAN NOT NULL DEFAULT FALSE,
    allow_exempted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (class_id,subject_id)
);
