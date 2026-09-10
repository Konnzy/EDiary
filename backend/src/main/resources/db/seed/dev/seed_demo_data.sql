-- Development-only data. Run manually after Flyway has created the schema.
-- Never load this file in production.
--
-- Admin:  admin.test   / AdminPass!234 / ABC-123
-- Teacher: teacher.test / AdminPass!234
-- Student: student.test / AdminPass!234

BEGIN;

INSERT INTO App_user (
    user_login,
    user_email,
    user_password_hash,
    user_role,
    user_status,
    user_must_change_password
)
VALUES (
    'admin.test',
    'admin.test@school.edu.ua',
    '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC',
    'ADMIN',
    'ACTIVE',
    FALSE
)
ON CONFLICT DO NOTHING;

INSERT INTO Administrator (
    user_id,
    admin_name,
    admin_surname,
    admin_code_hash
)
SELECT
    user_id,
    'Олена',
    'Ткаченко',
    '$2a$12$nK3iVdvecqcGP7Jgx1a12Oe0MxiDsB1TuRVO..YWZV2N0JrVsmb62'
FROM App_user
WHERE user_login = 'admin.test'
  AND user_role = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO Subject (subject_title)
VALUES ('Математика')
ON CONFLICT (subject_title) DO NOTHING;

INSERT INTO App_user (
    user_login,
    user_email,
    user_password_hash,
    user_role,
    user_status,
    user_must_change_password
)
VALUES (
    'teacher.test',
    'teacher.test@school.edu.ua',
    '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC',
    'TEACHER',
    'ACTIVE',
    FALSE
)
ON CONFLICT DO NOTHING;

INSERT INTO Teacher (
    user_id,
    teacher_name,
    teacher_surname,
    teacher_patronymic,
    teacher_birthdate,
    teacher_email,
    teacher_phone
)
SELECT
    user_id,
    'Олександр',
    'Коваленко',
    'Іванович',
    DATE '1987-03-18',
    user_email,
    '+380671112233'
FROM App_user
WHERE user_login = 'teacher.test'
  AND user_role = 'TEACHER'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO Teacher_subject (teacher_id, subject_id)
SELECT teacher.teacher_id, subject.subject_id
FROM Teacher teacher
JOIN App_user app_user ON app_user.user_id = teacher.user_id
CROSS JOIN Subject subject
WHERE app_user.user_login = 'teacher.test'
  AND subject.subject_title = 'Математика'
ON CONFLICT (teacher_id, subject_id) DO NOTHING;

INSERT INTO School_class (
    class_teacher_id,
    class_number,
    class_letter,
    class_limit
)
SELECT teacher.teacher_id, 9, 'А', 30
FROM Teacher teacher
JOIN App_user app_user ON app_user.user_id = teacher.user_id
WHERE app_user.user_login = 'teacher.test'
ON CONFLICT (class_number, class_letter) DO NOTHING;

INSERT INTO App_user (
    user_login,
    user_email,
    user_password_hash,
    user_role,
    user_status,
    user_must_change_password
)
VALUES (
    'student.test',
    'student.test@school.edu.ua',
    '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC',
    'STUDENT',
    'ACTIVE',
    FALSE
)
ON CONFLICT DO NOTHING;

INSERT INTO Student (
    user_id,
    class_id,
    student_name,
    student_surname,
    student_birthdate,
    student_enrollment_date,
    student_status
)
SELECT
    app_user.user_id,
    school_class.class_id,
    'Іван',
    'Петренко',
    DATE '2011-08-12',
    DATE '2025-09-01',
    'STUDYING'
FROM App_user app_user
CROSS JOIN School_class school_class
WHERE app_user.user_login = 'student.test'
  AND app_user.user_role = 'STUDENT'
  AND school_class.class_number = 9
  AND school_class.class_letter = 'А'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO User_notification_settings (user_id)
SELECT user_id
FROM App_user
WHERE user_login IN ('admin.test', 'teacher.test', 'student.test')
ON CONFLICT (user_id) DO NOTHING;

COMMIT;
