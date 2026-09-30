CREATE TABLE user_notification (
    notification_id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    category VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(500) NOT NULL,
    target VARCHAR(500) NOT NULL,
    event_key VARCHAR(160),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at TIMESTAMP
);

CREATE INDEX idx_user_notification_recipient_created
    ON user_notification (recipient_id, created_at DESC, notification_id DESC);
CREATE UNIQUE INDEX idx_user_notification_event_key
    ON user_notification (recipient_id, event_key) WHERE event_key IS NOT NULL;
