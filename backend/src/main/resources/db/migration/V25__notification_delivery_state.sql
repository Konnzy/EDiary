ALTER TABLE user_notification
    ADD COLUMN dismissed_at TIMESTAMP,
    ADD COLUMN withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN visible_in_app BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN attendance_student_id BIGINT REFERENCES student(student_id) ON DELETE CASCADE,
    ADD COLUMN attendance_lesson_id BIGINT REFERENCES lesson(lesson_id) ON DELETE CASCADE,
    ADD COLUMN attendance_status VARCHAR(32),
    ADD COLUMN attendance_notice_seen BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_emailed_absent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_kind VARCHAR(24) CHECK (email_kind IN ('ABSENCE', 'CORRECTION')),
    ADD COLUMN email_due_at TIMESTAMP,
    ADD COLUMN email_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN email_sent_at TIMESTAMP;

CREATE INDEX idx_notification_email_due ON user_notification(email_due_at, notification_id)
    WHERE email_due_at IS NOT NULL;

-- Legacy notices contain only subject/date text. Link only unambiguous matches;
-- never enqueue email for historical notices during migration.
WITH candidates AS (
    SELECT n.notification_id, n.recipient_id, n.created_at, n.read_at,
           a.student_id, a.lesson_id, a.attendance_status::text AS status,
           COUNT(*) OVER (PARTITION BY n.notification_id) AS matches
    FROM user_notification n
    JOIN attendance a ON TRUE
    JOIN student st ON st.student_id = a.student_id
    JOIN lesson l ON l.lesson_id = a.lesson_id
    JOIN schedule s ON s.schedule_id = l.schedule_id
    JOIN subject subject ON subject.subject_id = s.subject_id
    WHERE n.category = 'ABSENCE' AND n.event_key IS NULL AND (
        (n.recipient_id = st.user_id AND n.body = subject.subject_title || ' · ' || l.lesson_date::text)
        OR (n.body = st.student_surname || ' ' || st.student_name || ' · ' || subject.subject_title || ' · ' || l.lesson_date::text
            AND EXISTS (SELECT 1 FROM student_parents sp JOIN parent p ON p.parent_id = sp.parent_id
                        WHERE sp.student_id = st.student_id AND p.user_id = n.recipient_id))
    )
), ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY recipient_id, student_id, lesson_id ORDER BY created_at DESC, notification_id DESC) AS position,
           BOOL_OR(read_at IS NOT NULL) OVER (PARTITION BY recipient_id, student_id, lesson_id) AS was_seen
    FROM candidates WHERE matches = 1
)
UPDATE user_notification n SET
    event_key = CASE WHEN ranked.position = 1 THEN 'attendance:' || ranked.student_id || ':' || ranked.lesson_id ELSE NULL END,
    attendance_student_id = CASE WHEN ranked.position = 1 THEN ranked.student_id ELSE NULL END,
    attendance_lesson_id = CASE WHEN ranked.position = 1 THEN ranked.lesson_id ELSE NULL END,
    attendance_status = CASE WHEN ranked.position = 1 THEN ranked.status ELSE NULL END,
    attendance_notice_seen = ranked.was_seen,
    withdrawn = ranked.position > 1 OR (ranked.status <> 'ABSENT' AND NOT ranked.was_seen),
    title = CASE WHEN ranked.position = 1 AND ranked.status <> 'ABSENT' THEN 'Відвідування уточнено' ELSE n.title END,
    body = CASE WHEN ranked.position = 1 AND ranked.status <> 'ABSENT' THEN LEFT(n.body, 400) || ' · Попередня позначка відсутності більше не актуальна.' ELSE n.body END,
    read_at = CASE WHEN ranked.position = 1 AND ranked.status <> 'ABSENT' THEN NULL ELSE n.read_at END
FROM ranked WHERE n.notification_id = ranked.notification_id;

CREATE TABLE notification_in_app_preference (
    settings_id BIGINT NOT NULL REFERENCES user_notification_settings(settings_id) ON DELETE CASCADE,
    preference_key VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (settings_id, preference_key)
);

INSERT INTO notification_in_app_preference(settings_id, preference_key, enabled)
SELECT settings_id, preference.key, preference.enabled
FROM user_notification_settings
CROSS JOIN LATERAL (VALUES
    ('newGrade', grade_email_enabled), ('newHomework', homework_email_enabled),
    ('absence', attendance_email_enabled), ('newMessage', message_email_enabled),
    ('scheduleChange', schedule_change_email_enabled),
    ('classScheduleChange', class_schedule_change_email_enabled),
    ('prolongedAbsence', prolonged_absence_email_enabled),
    ('classRosterChange', class_roster_change_email_enabled),
    ('homeworkDeadlineReminder', homework_deadline_reminder_email_enabled)
) AS preference(key, enabled);
