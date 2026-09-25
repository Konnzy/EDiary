ALTER TABLE lesson
    ADD COLUMN lesson_work_type VARCHAR(24) NOT NULL DEFAULT 'CLASSWORK',
    ADD CONSTRAINT chk_lesson_work_type CHECK (lesson_work_type IN (
        'CLASSWORK', 'CONTROL', 'HOMEWORK', 'INDEPENDENT',
        'PRACTICAL', 'LABORATORY', 'THEMATIC'
    ));

CREATE INDEX idx_mark_lesson ON mark (lesson_id);
CREATE INDEX idx_attendance_lesson ON attendance (lesson_id);
CREATE INDEX idx_homework_lesson ON homework (lesson_id);
