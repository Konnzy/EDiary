-- Development-only test users. Safe to run repeatedly.
-- Password for every account: AdminPass!234

BEGIN;

INSERT INTO app_user (
    user_login,
    user_email,
    user_password_hash,
    user_role,
    user_status,
    user_must_change_password
)
VALUES
    ('student.ivanenko', 'student.ivanenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'STUDENT', 'ACTIVE', FALSE),
    ('student.bondarenko', 'student.bondarenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'STUDENT', 'ACTIVE', FALSE),
    ('student.shevchenko', 'student.shevchenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'STUDENT', 'ACTIVE', FALSE),
    ('student.melnyk', 'student.melnyk@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'STUDENT', 'ACTIVE', FALSE),
    ('teacher.ivanenko', 'teacher.ivanenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'TEACHER', 'ACTIVE', FALSE),
    ('teacher.bondarenko', 'teacher.bondarenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'TEACHER', 'ACTIVE', FALSE),
    ('teacher.shevchenko', 'teacher.shevchenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'TEACHER', 'ACTIVE', FALSE),
    ('parent.ivanenko', 'parent.ivanenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'PARENT', 'ACTIVE', FALSE),
    ('parent.bondarenko', 'parent.bondarenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'PARENT', 'ACTIVE', FALSE),
    ('parent.shevchenko', 'parent.shevchenko@school.edu.ua', '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC', 'PARENT', 'ACTIVE', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO student (
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
    (SELECT class_id FROM school_class ORDER BY class_number, class_letter LIMIT 1),
    data.first_name,
    data.last_name,
    data.birth_date,
    DATE '2026-09-01',
    'STUDYING'
FROM (
    VALUES
        ('student.ivanenko', 'Артем', 'Іваненко', DATE '2011-02-14'),
        ('student.bondarenko', 'Марія', 'Бондаренко', DATE '2010-11-03'),
        ('student.shevchenko', 'Софія', 'Шевченко', DATE '2011-06-22'),
        ('student.melnyk', 'Максим', 'Мельник', DATE '2010-09-09')
) AS data(login, first_name, last_name, birth_date)
JOIN app_user ON app_user.user_login = data.login
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO teacher (
    user_id,
    teacher_name,
    teacher_surname,
    teacher_patronymic,
    teacher_birthdate,
    teacher_email,
    teacher_phone
)
SELECT
    app_user.user_id,
    data.first_name,
    data.last_name,
    data.patronymic,
    data.birth_date,
    app_user.user_email,
    data.phone
FROM (
    VALUES
        ('teacher.ivanenko', 'Наталія', 'Іваненко', 'Сергіївна', DATE '1985-04-17', '+380671110001'),
        ('teacher.bondarenko', 'Андрій', 'Бондаренко', 'Олегович', DATE '1981-08-25', '+380671110002'),
        ('teacher.shevchenko', 'Оксана', 'Шевченко', 'Вікторівна', DATE '1990-12-05', '+380671110003')
) AS data(login, first_name, last_name, patronymic, birth_date, phone)
JOIN app_user ON app_user.user_login = data.login
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO parent (
    user_id,
    parent_name,
    parent_surname,
    parent_patronymic,
    parent_birthdate,
    parent_email,
    parent_phone
)
SELECT
    app_user.user_id,
    data.first_name,
    data.last_name,
    data.patronymic,
    data.birth_date,
    app_user.user_email,
    data.phone
FROM (
    VALUES
        ('parent.ivanenko', 'Олена', 'Іваненко', 'Петрівна', DATE '1984-07-12', '+380672220001'),
        ('parent.bondarenko', 'Віктор', 'Бондаренко', 'Ігорович', DATE '1980-01-28', '+380672220002'),
        ('parent.shevchenko', 'Ірина', 'Шевченко', 'Миколаївна', DATE '1987-10-16', '+380672220003')
) AS data(login, first_name, last_name, patronymic, birth_date, phone)
JOIN app_user ON app_user.user_login = data.login
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO teacher_subject (teacher_id, subject_id)
SELECT teacher.teacher_id, subject.subject_id
FROM (
    VALUES
        ('teacher.ivanenko', 'Українська мова'),
        ('teacher.ivanenko', 'Українська література'),
        ('teacher.bondarenko', 'Інформатика'),
        ('teacher.shevchenko', 'Математика')
) AS assignment(login, subject_title)
JOIN app_user ON app_user.user_login = assignment.login
JOIN teacher ON teacher.user_id = app_user.user_id
JOIN subject ON subject.subject_title = assignment.subject_title
ON CONFLICT (teacher_id, subject_id) DO NOTHING;

INSERT INTO student_parents (student_id, parent_id, is_primary_contact)
SELECT student.student_id, parent.parent_id, TRUE
FROM (
    VALUES
        ('student.ivanenko', 'parent.ivanenko'),
        ('student.bondarenko', 'parent.bondarenko'),
        ('student.shevchenko', 'parent.shevchenko')
) AS relation(student_login, parent_login)
JOIN app_user student_user ON student_user.user_login = relation.student_login
JOIN student ON student.user_id = student_user.user_id
JOIN app_user parent_user ON parent_user.user_login = relation.parent_login
JOIN parent ON parent.user_id = parent_user.user_id
ON CONFLICT (student_id, parent_id) DO NOTHING;

INSERT INTO user_notification_settings (user_id)
SELECT user_id
FROM app_user
WHERE user_login IN (
    'student.ivanenko',
    'student.bondarenko',
    'student.shevchenko',
    'student.melnyk',
    'teacher.ivanenko',
    'teacher.bondarenko',
    'teacher.shevchenko',
    'parent.ivanenko',
    'parent.bondarenko',
    'parent.shevchenko'
)
ON CONFLICT (user_id) DO NOTHING;

COMMIT;
