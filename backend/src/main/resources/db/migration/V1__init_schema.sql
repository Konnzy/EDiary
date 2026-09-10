--- ---------------------------------
-- Database Schema for Electronic Diary System
-- ---------------------------------

CREATE TYPE enum_user_role AS ENUM (
    'ADMIN',
    'TEACHER',
    'PARENT',
    'STUDENT'
);

CREATE TYPE enum_student_status AS ENUM (
    'STUDYING',
    'GRADUATED',
    'EXPELLED'
);

CREATE TYPE enum_mark_type AS ENUM (
    'CURRENT',
    'TOPIC',
    'SEMESTER',
    'YEAR',
    'TEST',
    'INDEPENDENT_WORK',
    'HOMEWORK'
);

CREATE TYPE enum_attendance_status AS ENUM (
    'PRESENT',
    'ABSENT',
    'LATE',
    'EXCUSED_ABSENCE'
);

CREATE TYPE enum_homework_status AS ENUM (
    'ASSIGNED',
    'COMPLETED',
    'OVERDUE',
    'SUBMITTED_LATE'
);

CREATE TYPE enum_weekday AS ENUM(
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY'
);

CREATE TYPE enum_registration_status AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED'
);

CREATE TYPE enum_user_status AS ENUM (
    'ACTIVE',
    'BLOCKED',
    'DELETED'
);

CREATE TABLE App_user (
    user_id BIGSERIAL PRIMARY KEY,
    user_login VARCHAR(50) NOT NULL UNIQUE,
    user_email VARCHAR(100) NOT NULL UNIQUE,
    user_password_hash VARCHAR(255) NOT NULL,
    user_role enum_user_role NOT NULL,
    user_status enum_user_status NOT NULL DEFAULT 'ACTIVE',
    user_must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    user_created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    user_updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE Teacher(
	teacher_id BIGSERIAL PRIMARY KEY,
	user_id BIGINT NOT NULL UNIQUE REFERENCES App_user(user_id),
	teacher_name VARCHAR(50) NOT NULL,
	teacher_surname VARCHAR(50) NOT NULL,
    teacher_patronymic VARCHAR(50),
	teacher_birthdate DATE NOT NULL,
    teacher_email VARCHAR(100) NOT NULL,
	teacher_phone VARCHAR(20) NOT NULL
);

CREATE TABLE School_class(
	class_id BIGSERIAL PRIMARY KEY,
	class_teacher_id BIGINT UNIQUE REFERENCES Teacher(teacher_id),
	class_number INT NOT NULL,
	class_letter VARCHAR(5) NOT NULL,
	class_limit INT NOT NULL DEFAULT 30 CHECK (class_limit >= 10 AND class_limit <= 30),
	CONSTRAINT unique_class_number_letter UNIQUE (class_number, class_letter)
);

CREATE TABLE Student(
	student_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES App_user(user_id),
	class_id BIGINT NOT NULL REFERENCES School_class(class_id),
	student_name VARCHAR(50) NOT NULL,
	student_surname VARCHAR(50) NOT NULL,
	student_birthdate DATE NOT NULL,
	student_enrollment_date DATE NOT NULL,
	student_status enum_student_status NOT NULL
);

CREATE TABLE Parent(
	parent_id BIGSERIAL PRIMARY KEY,
	user_id BIGINT NOT NULL UNIQUE REFERENCES App_user(user_id),
	parent_name VARCHAR(50) NOT NULL,
    parent_surname VARCHAR(50) NOT NULL,
    parent_patronymic VARCHAR(50),
	parent_birthdate DATE NOT NULL,
    parent_email VARCHAR(100) NOT NULL,
	parent_phone VARCHAR(20) NOT NULL
);

CREATE TABLE Administrator
(
    admin_id      BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL UNIQUE REFERENCES App_user (user_id),
    admin_name    VARCHAR(50) NOT NULL,
    admin_surname VARCHAR(50) NOT NULL,
    admin_code_hash VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE Student_parents(
	student_id BIGINT NOT NULL REFERENCES Student(student_id),
	parent_id BIGINT NOT NULL REFERENCES Parent(parent_id),
    is_primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
	PRIMARY KEY (student_id, parent_id)
);

CREATE TABLE Subject(
	subject_id BIGSERIAL PRIMARY KEY,
	subject_title VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE Teacher_subject(
	teacher_id BIGINT NOT NULL REFERENCES Teacher(teacher_id),
	subject_id BIGINT NOT NULL REFERENCES Subject(subject_id),
	PRIMARY KEY (teacher_id, subject_id)
);

CREATE TABLE Schedule(
	schedule_id BIGSERIAL PRIMARY KEY,
	class_id BIGINT NOT NULL REFERENCES School_class(class_id),
	subject_id BIGINT NOT NULL REFERENCES Subject(subject_id),
	teacher_id BIGINT NOT NULL REFERENCES Teacher(teacher_id),
	schedule_weekday enum_weekday NOT NULL,
	schedule_lesson_number INT NOT NULL,
    schedule_room VARCHAR(20),
	CONSTRAINT check_lesson_number CHECK (
		schedule_lesson_number >= 1 AND schedule_lesson_number <= 8),
	schedule_start_time TIME NOT NULL,
	schedule_end_time TIME NOT NULL,
    CONSTRAINT fk_schedule_teacher_subject FOREIGN KEY (teacher_id, subject_id)
        REFERENCES Teacher_subject(teacher_id, subject_id),
    CONSTRAINT chk_schedule_duration CHECK (
        schedule_end_time > schedule_start_time
            AND (schedule_end_time - schedule_start_time) <= INTERVAL '45 minutes'),
    CONSTRAINT uq_class_schedule UNIQUE (
    class_id,
    schedule_weekday,
    schedule_lesson_number),
    CONSTRAINT uq_teacher_schedule UNIQUE (
    teacher_id,
    schedule_weekday,
    schedule_lesson_number)
);

CREATE TABLE Lesson(
	lesson_id BIGSERIAL PRIMARY KEY,
	schedule_id BIGINT NOT NULL REFERENCES Schedule(schedule_id),
	lesson_date DATE NOT NULL,
	lesson_topic VARCHAR(200),
    CONSTRAINT unique_lesson_per_schedule_date UNIQUE(schedule_id, lesson_date)
);

CREATE TABLE Mark(
	mark_id BIGSERIAL PRIMARY KEY,
	student_id BIGINT NOT NULL REFERENCES Student(student_id),
	lesson_id BIGINT NOT NULL REFERENCES Lesson(lesson_id),
    mark_date DATE NOT NULL DEFAULT CURRENT_DATE,
    mark_type enum_mark_type NOT NULL,
	mark_score INT NOT NULL,
	mark_comment VARCHAR(200),
    CONSTRAINT check_score
        CHECK (mark_score >= 1 AND mark_score <= 12),
	CONSTRAINT unique_mark_per_lesson
        UNIQUE(student_id, lesson_id, mark_type)
);

CREATE TABLE Attendance(
    attendance_id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES Student(student_id),
    lesson_id BIGINT NOT NULL REFERENCES Lesson(lesson_id),
    attendance_status enum_attendance_status NOT NULL,
    attendance_reason VARCHAR(200),
    attendance_comment VARCHAR(200),
    CONSTRAINT unique_attendance_per_lesson UNIQUE (student_id, lesson_id)
);

CREATE TABLE Homework(
    homework_id BIGSERIAL PRIMARY KEY,
    lesson_id BIGINT NOT NULL REFERENCES Lesson(lesson_id),
    homework_date DATE NOT NULL DEFAULT CURRENT_DATE,
    homework_description VARCHAR(500) NOT NULL,
    homework_deadline DATE,
    homework_comment VARCHAR(200),
    CONSTRAINT chk_homework_deadline CHECK (homework_deadline IS NULL OR homework_deadline >= homework_date)
);

CREATE TABLE Student_homework(
    student_homework_id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES Student(student_id),
    homework_id BIGINT NOT NULL REFERENCES Homework(homework_id),
    homework_status enum_homework_status NOT NULL DEFAULT 'ASSIGNED',
    student_homework_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    student_homework_comment VARCHAR(200),
    CONSTRAINT unique_student_homework UNIQUE(student_id, homework_id)
);

CREATE TABLE Message(
    message_id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES App_user(user_id),
    receiver_id BIGINT NOT NULL REFERENCES App_user(user_id),
    message_subject VARCHAR(150) NOT NULL,
    message_text VARCHAR(1000) NOT NULL,
    message_created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    message_is_read BOOLEAN NOT NULL DEFAULT FALSE,
    message_is_important BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE User_notification_settings (
    settings_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES App_user(user_id),
    grade_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    homework_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    attendance_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,

    homework_status_email_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE Registration_request (
    request_id BIGSERIAL PRIMARY KEY,
    request_name VARCHAR(50) NOT NULL,
    request_surname VARCHAR(50) NOT NULL,
    request_email VARCHAR(100) NOT NULL,
    request_phone VARCHAR(20) NOT NULL,
    request_birthdate DATE NOT NULL,
    requested_role enum_user_role NOT NULL,
    request_status enum_registration_status NOT NULL DEFAULT 'PENDING',
    request_terms_accepted_at TIMESTAMP NOT NULL,
    request_created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    request_reviewed_at TIMESTAMP,
    request_reviewed_by_user_id BIGINT REFERENCES App_user(user_id),
    request_rejection_reason VARCHAR(500),
    request_processed_user_id BIGINT UNIQUE REFERENCES App_user(user_id),
    requested_class_id BIGINT REFERENCES School_class(class_id),
    requested_subject_id BIGINT REFERENCES Subject(subject_id),
    request_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_registration_role_not_admin CHECK (requested_role <> 'ADMIN'),
    CONSTRAINT chk_registration_requested_data CHECK (
        (requested_role IN ('STUDENT', 'PARENT')
            AND requested_class_id IS NOT NULL
            AND requested_subject_id IS NULL)
            OR (requested_role = 'TEACHER'
                AND requested_subject_id IS NOT NULL
                AND requested_class_id IS NULL)
    ),
    CONSTRAINT chk_registration_review_state CHECK (
        (request_status = 'PENDING'
            AND request_reviewed_at IS NULL
            AND request_reviewed_by_user_id IS NULL
            AND request_rejection_reason IS NULL
            AND request_processed_user_id IS NULL)
        OR (request_status = 'APPROVED'
            AND request_reviewed_at IS NOT NULL
            AND request_reviewed_by_user_id IS NOT NULL
            AND request_rejection_reason IS NULL
            AND request_processed_user_id IS NOT NULL)
        OR (request_status = 'REJECTED'
            AND request_reviewed_at IS NOT NULL
            AND request_reviewed_by_user_id IS NOT NULL
            AND request_rejection_reason IS NOT NULL
            AND request_processed_user_id IS NULL)
    )
);

CREATE TABLE Password_reset_token (
    token_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES App_user(user_id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    token_expires_at TIMESTAMP NOT NULL,
    token_used BOOLEAN NOT NULL DEFAULT FALSE,
    token_created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE User_session (
    session_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES App_user(user_id),
    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,
    session_ip_address VARCHAR(45),
    session_user_agent VARCHAR(500),
    session_created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    session_last_used_at TIMESTAMP,
    session_expires_at TIMESTAMP NOT NULL,
    session_revoked_at TIMESTAMP,
    session_revoked_reason VARCHAR(200),
    CONSTRAINT chk_user_session_expiration CHECK (session_expires_at > session_created_at),
    CONSTRAINT chk_user_session_revoked_reason CHECK (
        session_revoked_at IS NULL OR session_revoked_reason IS NOT NULL
    )
);

CREATE UNIQUE INDEX unique_primary_parent_per_student
    ON Student_parents(student_id)
    WHERE is_primary_contact = TRUE;

CREATE UNIQUE INDEX uq_app_user_login_ci ON App_user(LOWER(user_login));
CREATE UNIQUE INDEX uq_app_user_email_ci ON App_user(LOWER(user_email));

CREATE INDEX idx_student_class_id ON Student(class_id);

CREATE INDEX idx_school_class_teacher_id ON School_class(class_teacher_id);

CREATE INDEX idx_student_parents_parent_id ON Student_parents(parent_id);

CREATE INDEX idx_teacher_subject_subject_id ON Teacher_subject(subject_id);

CREATE INDEX idx_schedule_subject_id ON Schedule(subject_id);

CREATE INDEX idx_mark_student_id ON Mark(student_id);
CREATE INDEX idx_mark_lesson_id ON Mark(lesson_id);
CREATE INDEX idx_mark_date ON Mark(mark_date);

CREATE INDEX idx_attendance_student_id ON Attendance(student_id);
CREATE INDEX idx_attendance_lesson_id ON Attendance(lesson_id);

CREATE INDEX idx_homework_lesson_id ON Homework(lesson_id);
CREATE INDEX idx_homework_deadline ON Homework(homework_deadline);

CREATE INDEX idx_student_homework_homework_id ON Student_homework(homework_id);
CREATE INDEX idx_student_homework_status ON Student_homework(homework_status);

CREATE INDEX idx_message_sender_id ON Message(sender_id);
CREATE INDEX idx_message_receiver_read ON Message(receiver_id, message_is_read);
CREATE INDEX idx_message_created_at ON Message(message_created_at);

CREATE INDEX idx_registration_request_status ON Registration_request(request_status);
CREATE INDEX idx_registration_request_role ON Registration_request(requested_role);
CREATE INDEX idx_registration_request_email ON Registration_request(request_email);
CREATE INDEX idx_registration_request_created_at ON Registration_request(request_created_at DESC);
CREATE UNIQUE INDEX uq_pending_registration_email_ci
    ON Registration_request(LOWER(request_email))
    WHERE request_status = 'PENDING';

CREATE INDEX idx_password_reset_token_user_id ON Password_reset_token(user_id);
CREATE INDEX idx_password_reset_token_expires_at ON Password_reset_token(token_expires_at);
CREATE INDEX idx_password_reset_token_active
    ON Password_reset_token(user_id, token_expires_at)
    WHERE token_used = FALSE;

CREATE INDEX idx_user_session_user_id ON User_session(user_id);
CREATE INDEX idx_user_session_expires_at ON User_session(session_expires_at);
CREATE INDEX idx_user_session_active
    ON User_session(user_id, session_expires_at)
    WHERE session_revoked_at IS NULL;
