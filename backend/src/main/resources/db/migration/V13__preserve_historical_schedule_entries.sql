ALTER TABLE schedule
    ADD COLUMN schedule_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN schedule_archived_at TIMESTAMP,
    ADD CONSTRAINT chk_schedule_archive_status CHECK (
        (schedule_active AND schedule_archived_at IS NULL)
        OR (NOT schedule_active AND schedule_archived_at IS NOT NULL)
    );

ALTER TABLE schedule
    DROP CONSTRAINT uq_class_schedule,
    DROP CONSTRAINT uq_teacher_schedule;

CREATE UNIQUE INDEX uq_active_class_schedule
    ON schedule (class_id, schedule_academic_year, schedule_week_number,
                 schedule_weekday, schedule_lesson_number)
    WHERE schedule_active;

CREATE UNIQUE INDEX uq_active_teacher_schedule
    ON schedule (teacher_id, schedule_academic_year, schedule_week_number,
                 schedule_weekday, schedule_lesson_number)
    WHERE schedule_active;

CREATE INDEX idx_lesson_schedule_id ON lesson (schedule_id);

CREATE FUNCTION protect_historical_schedule_entry()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF EXISTS (SELECT 1 FROM lesson WHERE schedule_id = OLD.schedule_id) THEN
            RAISE EXCEPTION 'Cannot delete a schedule entry with recorded lessons';
        END IF;
        RETURN OLD;
    END IF;

    IF NOT OLD.schedule_active AND NEW.schedule_active THEN
        RAISE EXCEPTION 'Cannot reactivate a historical schedule entry';
    END IF;

    IF EXISTS (SELECT 1 FROM lesson WHERE schedule_id = OLD.schedule_id)
        AND (OLD.class_id IS DISTINCT FROM NEW.class_id
            OR OLD.subject_id IS DISTINCT FROM NEW.subject_id
            OR OLD.teacher_id IS DISTINCT FROM NEW.teacher_id
            OR OLD.schedule_weekday IS DISTINCT FROM NEW.schedule_weekday
            OR OLD.schedule_lesson_number IS DISTINCT FROM NEW.schedule_lesson_number
            OR OLD.schedule_room IS DISTINCT FROM NEW.schedule_room
            OR OLD.schedule_start_time IS DISTINCT FROM NEW.schedule_start_time
            OR OLD.schedule_end_time IS DISTINCT FROM NEW.schedule_end_time
            OR OLD.schedule_academic_year IS DISTINCT FROM NEW.schedule_academic_year
            OR OLD.schedule_week_number IS DISTINCT FROM NEW.schedule_week_number) THEN
        RAISE EXCEPTION 'Cannot change a schedule entry with recorded lessons';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_historical_schedule_entry
BEFORE UPDATE OR DELETE ON schedule
FOR EACH ROW
EXECUTE FUNCTION protect_historical_schedule_entry();
