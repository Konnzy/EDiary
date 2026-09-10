CREATE TABLE academic_year (
    academic_year_value VARCHAR(9) PRIMARY KEY,
    CONSTRAINT chk_academic_year_format CHECK (
        academic_year_value ~ '^[0-9]{4}/[0-9]{4}$'
        AND substring(academic_year_value, 6, 4)::INT
            = substring(academic_year_value, 1, 4)::INT + 1
    )
);

INSERT INTO academic_year (academic_year_value)
VALUES ('2026/2027');

ALTER TABLE schedule
    ADD COLUMN schedule_academic_year VARCHAR(9) NOT NULL DEFAULT '2026/2027'
        REFERENCES academic_year(academic_year_value),
    ADD COLUMN schedule_week_number SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_schedule_week_number CHECK (schedule_week_number IN (1, 2));

ALTER TABLE schedule
    DROP CONSTRAINT uq_class_schedule,
    DROP CONSTRAINT uq_teacher_schedule;

ALTER TABLE schedule
    ADD CONSTRAINT uq_class_schedule UNIQUE (
        class_id,
        schedule_academic_year,
        schedule_week_number,
        schedule_weekday,
        schedule_lesson_number
    ),
    ADD CONSTRAINT uq_teacher_schedule UNIQUE (
        teacher_id,
        schedule_academic_year,
        schedule_week_number,
        schedule_weekday,
        schedule_lesson_number
    );

CREATE INDEX idx_schedule_class_period
    ON schedule(class_id, schedule_academic_year, schedule_week_number);
