ALTER TABLE registration_request
    ADD COLUMN request_patronymic VARCHAR(50);

ALTER TABLE student
    ADD COLUMN student_patronymic VARCHAR(50);
