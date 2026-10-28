-- Preserve existing email choices; only future settings rows default to opt-in.
ALTER TABLE user_notification_settings
    ALTER COLUMN grade_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN homework_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN homework_deadline_reminder_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN attendance_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN homework_status_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN message_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN schedule_change_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN class_schedule_change_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN prolonged_absence_email_enabled SET DEFAULT FALSE,
    ALTER COLUMN class_roster_change_email_enabled SET DEFAULT FALSE;

-- Dismissed/withdrawn notices remain hidden by their own lifecycle flags.
UPDATE user_notification SET visible_in_app = TRUE WHERE visible_in_app = FALSE;
-- Historical notification_in_app_preference rows are retained but ignored.
