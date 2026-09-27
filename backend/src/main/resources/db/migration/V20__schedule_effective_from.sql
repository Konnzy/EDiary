ALTER TABLE schedule ADD COLUMN schedule_effective_from DATE;

-- Older schedules have no recorded creation date. Preserve their existing history.
UPDATE schedule
SET schedule_effective_from = make_date(split_part(schedule_academic_year, '/', 1)::integer, 9, 1);

ALTER TABLE schedule ALTER COLUMN schedule_effective_from SET NOT NULL;
