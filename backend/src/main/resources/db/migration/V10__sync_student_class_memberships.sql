CREATE OR REPLACE FUNCTION sync_student_class_membership()
RETURNS TRIGGER AS $$
DECLARE
    old_year VARCHAR(9);
    new_year VARCHAR(9);
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.class_id IS NOT DISTINCT FROM NEW.class_id THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.class_id IS NOT NULL THEN
        SELECT class_academic_year INTO old_year
        FROM school_class
        WHERE class_id = OLD.class_id;

        IF NEW.class_id IS NULL THEN
            UPDATE student_class_membership
            SET membership_active = FALSE, left_at = CURRENT_TIMESTAMP
            WHERE student_id = NEW.student_id
              AND class_id = OLD.class_id
              AND membership_active;
        ELSE
            SELECT class_academic_year INTO new_year
            FROM school_class
            WHERE class_id = NEW.class_id;

            IF old_year = new_year THEN
                UPDATE student_class_membership
                SET membership_active = FALSE, left_at = CURRENT_TIMESTAMP
                WHERE student_id = NEW.student_id
                  AND class_id = OLD.class_id
                  AND membership_active;
            END IF;
        END IF;
    END IF;

    IF NEW.class_id IS NOT NULL THEN
        INSERT INTO student_class_membership (student_id, class_id)
        VALUES (NEW.student_id, NEW.class_id)
        ON CONFLICT (student_id, class_id) DO UPDATE
        SET membership_active = TRUE,
            joined_at = CURRENT_TIMESTAMP,
            left_at = NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_student_class_membership
AFTER INSERT OR UPDATE OF class_id ON student
FOR EACH ROW
EXECUTE FUNCTION sync_student_class_membership();
