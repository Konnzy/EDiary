CREATE TYPE enum_lesson_status AS ENUM (
    'PLANNED',
    'IN_PROGRESS',
    'COMPLETED',
    'CANCELLED'
);

ALTER TABLE lesson
    ADD COLUMN lesson_status enum_lesson_status NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN lesson_created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lesson_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_lesson_date ON lesson (lesson_date);
