CREATE TABLE subject_assessment_policy (
    subject_id BIGINT PRIMARY KEY REFERENCES subject(subject_id) ON DELETE CASCADE,
    mode VARCHAR(16) NOT NULL DEFAULT 'NUMERIC' CHECK (mode IN ('NUMERIC','PASS_FAIL')),
    allow_passed BOOLEAN NOT NULL DEFAULT FALSE,
    allow_exempted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Preserve results; incompatible historical scales require an explicit decision.
DO $$
BEGIN
    IF EXISTS (
        SELECT sc.subject_id FROM mark m JOIN lesson l ON l.lesson_id=m.lesson_id
        JOIN schedule sc ON sc.schedule_id=l.schedule_id GROUP BY sc.subject_id
        HAVING bool_or(m.mark_score IS NOT NULL) AND bool_or(m.mark_result='NOT_PASSED')
    ) THEN
        RAISE EXCEPTION 'Cannot merge assessment policies: a subject contains both numeric marks and NOT_PASSED results. Resolve the subject grading scale before migration; existing results have not been changed.';
    END IF;
END $$;

INSERT INTO subject_assessment_policy(subject_id,mode,allow_passed,allow_exempted)
SELECT s.subject_id,
    CASE WHEN EXISTS (
        SELECT 1 FROM mark m JOIN lesson l ON l.lesson_id=m.lesson_id JOIN schedule sc ON sc.schedule_id=l.schedule_id
        WHERE sc.subject_id=s.subject_id AND m.mark_score IS NOT NULL
    ) THEN 'NUMERIC'
    WHEN EXISTS (SELECT 1 FROM assessment_policy p WHERE p.subject_id=s.subject_id AND p.mode='PASS_FAIL')
        OR EXISTS (SELECT 1 FROM mark m JOIN lesson l ON l.lesson_id=m.lesson_id JOIN schedule sc ON sc.schedule_id=l.schedule_id
            WHERE sc.subject_id=s.subject_id AND m.mark_result='NOT_PASSED') THEN 'PASS_FAIL'
    ELSE 'NUMERIC' END,
    EXISTS (SELECT 1 FROM assessment_policy p WHERE p.subject_id=s.subject_id AND (p.allow_passed OR p.mode='PASS_FAIL'))
        OR EXISTS (SELECT 1 FROM mark m JOIN lesson l ON l.lesson_id=m.lesson_id JOIN schedule sc ON sc.schedule_id=l.schedule_id
            WHERE sc.subject_id=s.subject_id AND m.mark_result='PASSED'),
    EXISTS (SELECT 1 FROM assessment_policy p WHERE p.subject_id=s.subject_id AND p.allow_exempted)
        OR EXISTS (SELECT 1 FROM mark m JOIN lesson l ON l.lesson_id=m.lesson_id JOIN schedule sc ON sc.schedule_id=l.schedule_id
            WHERE sc.subject_id=s.subject_id AND m.mark_result='EXEMPTED')
FROM subject s;

-- assessment_policy remains an unused historical snapshot of class-specific settings.
