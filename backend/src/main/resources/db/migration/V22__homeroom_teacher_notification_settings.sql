ALTER TABLE user_notification_settings
    ADD COLUMN class_schedule_change_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN prolonged_absence_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN class_roster_change_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
