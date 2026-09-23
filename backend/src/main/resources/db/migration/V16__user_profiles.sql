CREATE TABLE user_avatar (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(user_id) ON DELETE CASCADE,
    content_type VARCHAR(10) NOT NULL,
    image_data BYTEA NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_avatar_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_user_avatar_size CHECK (octet_length(image_data) <= 5242880)
);

ALTER TABLE user_notification_settings
    ADD COLUMN message_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN schedule_change_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
