ALTER TABLE user_notification_settings
    ADD COLUMN homework_deadline_reminder_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
