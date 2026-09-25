ALTER TABLE lesson DROP CONSTRAINT chk_lesson_work_type;

ALTER TABLE lesson ADD CONSTRAINT chk_lesson_work_type CHECK (lesson_work_type IN (
    'CLASSWORK', 'CONTROL', 'HOMEWORK', 'INDEPENDENT',
    'PRACTICAL', 'LABORATORY', 'THEMATIC', 'SEMESTER', 'YEAR'
));
