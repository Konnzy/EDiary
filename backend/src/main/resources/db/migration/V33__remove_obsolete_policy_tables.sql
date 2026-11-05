DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM assessment_policy old
        LEFT JOIN subject_assessment_policy current ON current.subject_id = old.subject_id
        WHERE current.subject_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot remove assessment_policy: a subject has no migrated assessment settings.';
    END IF;
END $$;

DROP TABLE notification_in_app_preference;
DROP TABLE assessment_policy;
