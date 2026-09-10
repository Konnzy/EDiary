ALTER TABLE school_class
    ADD CONSTRAINT chk_school_class_number
        CHECK (class_number BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_school_class_letter
        CHECK (class_letter ~ '^[А-ЯІЇЄҐ]$');
