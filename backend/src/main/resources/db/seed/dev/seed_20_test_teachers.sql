-- Development-only test teachers. Safe to run repeatedly.
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
SELECT
    data.login,
    data.login || '@school.edu.ua',
    '$2a$12$8HQkBW.6blCRkSURMF3iFec48IdamgMBleLapcX0XG7j/gil6jJXC',
    'TEACHER',
    'ACTIVE',
    FALSE
FROM (
    VALUES
        ('teacher.petrenko'),
        ('teacher.melnyk'),
        ('teacher.tkachenko'),
        ('teacher.boiko'),
        ('teacher.kravchenko'),
        ('teacher.moroz'),
        ('teacher.polishchuk'),
        ('teacher.savchuk'),
        ('teacher.bondar'),
        ('teacher.marchenko'),
        ('teacher.oliinyk'),
        ('teacher.lytvyn'),
        ('teacher.romaniuk'),
        ('teacher.sydorenko'),
        ('teacher.honcharenko'),
        ('teacher.mazur'),
        ('teacher.rudenko'),
        ('teacher.kovalchuk'),
        ('teacher.levchenko'),
        ('teacher.danyliuk')
) AS data(login)
ON CONFLICT DO NOTHING;

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
        ('teacher.petrenko', 'Світлана', 'Петренко', 'Миколаївна', DATE '1984-02-11', '+380673330001'),
        ('teacher.melnyk', 'Олег', 'Мельник', 'Васильович', DATE '1979-06-24', '+380673330002'),
        ('teacher.tkachenko', 'Ірина', 'Ткаченко', 'Олександрівна', DATE '1988-09-07', '+380673330003'),
        ('teacher.boiko', 'Віталій', 'Бойко', 'Сергійович', DATE '1982-12-19', '+380673330004'),
        ('teacher.kravchenko', 'Марина', 'Кравченко', 'Ігорівна', DATE '1991-04-03', '+380673330005'),
        ('teacher.moroz', 'Андрій', 'Мороз', 'Петрович', DATE '1986-08-15', '+380673330006'),
        ('teacher.polishchuk', 'Людмила', 'Поліщук', 'Вікторівна', DATE '1977-01-28', '+380673330007'),
        ('teacher.savchuk', 'Роман', 'Савчук', 'Анатолійович', DATE '1990-11-09', '+380673330008'),
        ('teacher.bondar', 'Наталія', 'Бондар', 'Романівна', DATE '1983-05-17', '+380673330009'),
        ('teacher.marchenko', 'Сергій', 'Марченко', 'Олегович', DATE '1975-10-21', '+380673330010'),
        ('teacher.oliinyk', 'Тетяна', 'Олійник', 'Андріївна', DATE '1989-03-13', '+380673330011'),
        ('teacher.lytvyn', 'Володимир', 'Литвин', 'Іванович', DATE '1981-07-05', '+380673330012'),
        ('teacher.romaniuk', 'Оксана', 'Романюк', 'Петрівна', DATE '1993-02-26', '+380673330013'),
        ('teacher.sydorenko', 'Михайло', 'Сидоренко', 'Юрійович', DATE '1985-09-30', '+380673330014'),
        ('teacher.honcharenko', 'Алла', 'Гончаренко', 'Василівна', DATE '1978-12-08', '+380673330015'),
        ('teacher.mazur', 'Дмитро', 'Мазур', 'Богданович', DATE '1992-06-16', '+380673330016'),
        ('teacher.rudenko', 'Вікторія', 'Руденко', 'Сергіївна', DATE '1987-04-22', '+380673330017'),
        ('teacher.kovalchuk', 'Павло', 'Ковальчук', 'Миколайович', DATE '1980-08-01', '+380673330018'),
        ('teacher.levchenko', 'Галина', 'Левченко', 'Іванівна', DATE '1994-01-14', '+380673330019'),
        ('teacher.danyliuk', 'Юрій', 'Данилюк', 'Олексійович', DATE '1986-11-27', '+380673330020')
) AS data(login, first_name, last_name, patronymic, birth_date, phone)
JOIN app_user ON app_user.user_login = data.login
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO teacher_subject (teacher_id, subject_id)
SELECT teacher.teacher_id, subject.subject_id
FROM (
    VALUES
        ('teacher.petrenko', 'Математика'),
        ('teacher.petrenko', 'Інформатика'),
        ('teacher.melnyk', 'Фізична культура'),
        ('teacher.tkachenko', 'Українська мова'),
        ('teacher.tkachenko', 'Українська література'),
        ('teacher.boiko', 'Інформатика'),
        ('teacher.kravchenko', 'Математика'),
        ('teacher.moroz', 'Фізична культура'),
        ('teacher.polishchuk', 'Українська література'),
        ('teacher.savchuk', 'Інформатика'),
        ('teacher.bondar', 'Українська мова'),
        ('teacher.bondar', 'Українська література'),
        ('teacher.marchenko', 'Математика'),
        ('teacher.oliinyk', 'Українська мова'),
        ('teacher.lytvyn', 'Фізична культура'),
        ('teacher.romaniuk', 'Математика'),
        ('teacher.romaniuk', 'Інформатика'),
        ('teacher.sydorenko', 'Інформатика'),
        ('teacher.honcharenko', 'Українська література'),
        ('teacher.mazur', 'Фізична культура'),
        ('teacher.rudenko', 'Українська мова'),
        ('teacher.kovalchuk', 'Математика'),
        ('teacher.levchenko', 'Українська література'),
        ('teacher.levchenko', 'Українська мова'),
        ('teacher.danyliuk', 'Інформатика')
) AS assignment(login, subject_title)
JOIN app_user ON app_user.user_login = assignment.login
JOIN teacher ON teacher.user_id = app_user.user_id
JOIN subject ON subject.subject_title = assignment.subject_title
ON CONFLICT (teacher_id, subject_id) DO NOTHING;

INSERT INTO user_notification_settings (user_id)
SELECT user_id
FROM app_user
WHERE user_login IN (
    'teacher.petrenko',
    'teacher.melnyk',
    'teacher.tkachenko',
    'teacher.boiko',
    'teacher.kravchenko',
    'teacher.moroz',
    'teacher.polishchuk',
    'teacher.savchuk',
    'teacher.bondar',
    'teacher.marchenko',
    'teacher.oliinyk',
    'teacher.lytvyn',
    'teacher.romaniuk',
    'teacher.sydorenko',
    'teacher.honcharenko',
    'teacher.mazur',
    'teacher.rudenko',
    'teacher.kovalchuk',
    'teacher.levchenko',
    'teacher.danyliuk'
)
ON CONFLICT (user_id) DO NOTHING;

COMMIT;
