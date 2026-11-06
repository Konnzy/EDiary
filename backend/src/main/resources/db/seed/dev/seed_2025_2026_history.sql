-- Local demo data only. Run after Flyway and after the 2026/2027 9-А
-- demo class has students and a two-week schedule. Never run in production.
BEGIN;

CREATE TEMP TABLE demo_history_source ON COMMIT DROP AS
SELECT c.class_id, c.class_teacher_id
FROM school_class c
JOIN teacher t ON t.teacher_id = c.class_teacher_id
JOIN app_user u ON u.user_id = t.user_id
WHERE c.class_academic_year = '2026/2027' AND c.class_number = 9
  AND c.class_letter = 'А' AND u.user_login = 'teacher.test';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM demo_history_source c
        JOIN student s ON s.class_id = c.class_id
        JOIN app_user u ON u.user_id = s.user_id
        WHERE u.user_login = 'student.ivanov'
    ) OR (SELECT count(DISTINCT schedule_week_number) FROM schedule
          JOIN demo_history_source USING (class_id) WHERE schedule_active) <> 2 THEN
        RAISE EXCEPTION 'Expected demo students and both schedule weeks in 9-А for 2026/2027';
    END IF;
END $$;

INSERT INTO academic_year (academic_year_value) VALUES ('2025/2026')
ON CONFLICT DO NOTHING;

INSERT INTO school_class (class_number, class_letter, class_academic_year, class_teacher_id)
SELECT 8, 'А', '2025/2026', class_teacher_id FROM demo_history_source
ON CONFLICT (class_academic_year, class_number, class_letter) DO NOTHING;

CREATE TEMP TABLE demo_history_class ON COMMIT DROP AS
SELECT c.class_id FROM school_class c
JOIN demo_history_source d ON d.class_teacher_id = c.class_teacher_id
WHERE c.class_academic_year = '2025/2026' AND c.class_number = 8 AND c.class_letter = 'А';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM demo_history_class) OR EXISTS (
        SELECT 1 FROM student_class_membership m
        JOIN demo_history_class c USING (class_id)
        JOIN student s USING (student_id)
        WHERE s.class_id IS DISTINCT FROM (SELECT class_id FROM demo_history_source)
    ) THEN
        RAISE EXCEPTION 'Existing 8-А does not match the demo cohort';
    END IF;
END $$;

INSERT INTO student_class_membership (student_id, class_id, membership_active, joined_at, left_at)
SELECT s.student_id, c.class_id, FALSE, DATE '2025-09-01', DATE '2026-06-01'
FROM student s JOIN demo_history_source d ON d.class_id = s.class_id
CROSS JOIN demo_history_class c
ON CONFLICT (student_id, class_id) DO NOTHING;

INSERT INTO schedule (class_id, subject_id, teacher_id, schedule_weekday, schedule_lesson_number,
    schedule_room, schedule_start_time, schedule_end_time, schedule_academic_year,
    schedule_week_number, schedule_effective_from)
SELECT c.class_id, s.subject_id, s.teacher_id, s.schedule_weekday, s.schedule_lesson_number,
    s.schedule_room, s.schedule_start_time, s.schedule_end_time, '2025/2026',
    s.schedule_week_number, DATE '2025-09-01'
FROM schedule s JOIN demo_history_source d ON d.class_id = s.class_id
CROSS JOIN demo_history_class c
WHERE s.schedule_active AND s.schedule_academic_year = '2026/2027'
ON CONFLICT DO NOTHING;

-- Pick a real timetable slot for each assessment, respecting both cycle weeks.
CREATE TEMP TABLE demo_history_assessments ON COMMIT DROP AS
WITH periods (stage, first_day, work_type, topic, mark_type) AS (
    VALUES
        (1, DATE '2025-11-10', 'THEMATIC', 'Тематичне оцінювання за І семестр', 'TOPIC'),
        (2, DATE '2025-12-08', 'SEMESTER', 'Підсумок І семестру', 'SEMESTER'),
        (3, DATE '2026-02-16', 'THEMATIC', 'Тематичне оцінювання за ІІ семестр', 'TOPIC'),
        (4, DATE '2026-05-04', 'SEMESTER', 'Підсумок ІІ семестру', 'SEMESTER'),
        (5, DATE '2026-05-18', 'YEAR', 'Підсумок за навчальний рік', 'YEAR')
)
SELECT DISTINCT ON (s.subject_id, p.stage)
    s.subject_id, s.schedule_id, d.day AS lesson_date, p.stage, p.work_type, p.topic,
    p.mark_type::enum_mark_type AS mark_type, COALESCE(policy.mode, 'NUMERIC') AS mode
FROM schedule s JOIN demo_history_class c USING (class_id)
LEFT JOIN subject_assessment_policy policy USING (subject_id)
CROSS JOIN periods p
CROSS JOIN LATERAL generate_series(0, 13) AS offset_days(n)
CROSS JOIN LATERAL (SELECT p.first_day + offset_days.n AS day) d
WHERE s.schedule_active AND s.schedule_academic_year = '2025/2026'
  AND s.schedule_week_number = 1 + ((d.day - DATE '2025-09-01') / 7) % 2
  AND s.schedule_weekday::text = (ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'])[extract(isodow FROM d.day)::int]
  AND (p.work_type <> 'THEMATIC' OR COALESCE(policy.mode, 'NUMERIC') = 'NUMERIC')
ORDER BY s.subject_id, p.stage, d.day, s.schedule_lesson_number, s.schedule_id;

INSERT INTO lesson (schedule_id, lesson_date, lesson_work_type, lesson_topic, lesson_status,
    lesson_created_at, lesson_updated_at)
SELECT schedule_id, lesson_date, work_type, topic, 'COMPLETED', lesson_date, lesson_date
FROM demo_history_assessments
ON CONFLICT (schedule_id, lesson_date) DO NOTHING;

INSERT INTO mark (student_id, lesson_id, mark_date, mark_type, mark_score, mark_result)
SELECT s.student_id, l.lesson_id, a.lesson_date, a.mark_type,
    CASE WHEN a.mode = 'NUMERIC' THEN 7 + (s.student_id + a.subject_id) % 5
         + CASE WHEN a.stage >= 3 THEN 1 ELSE 0 END END,
    CASE WHEN a.mode = 'PASS_FAIL' THEN 'PASSED' END
FROM demo_history_assessments a
JOIN lesson l ON l.schedule_id = a.schedule_id AND l.lesson_date = a.lesson_date
    AND l.lesson_work_type = a.work_type AND l.lesson_topic = a.topic
JOIN schedule slot ON slot.schedule_id = a.schedule_id
JOIN student_class_membership s ON s.class_id = slot.class_id
JOIN student current_student USING (student_id)
JOIN demo_history_source d ON d.class_id = current_student.class_id
ON CONFLICT (student_id, lesson_id, mark_type) DO NOTHING;

COMMIT;
