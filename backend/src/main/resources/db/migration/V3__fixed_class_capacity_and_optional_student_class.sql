UPDATE school_class
SET class_limit = 30
WHERE class_limit <> 30;

ALTER TABLE school_class
    ADD CONSTRAINT chk_school_class_fixed_limit
        CHECK (class_limit = 30);

ALTER TABLE student
    ALTER COLUMN class_id DROP NOT NULL;
