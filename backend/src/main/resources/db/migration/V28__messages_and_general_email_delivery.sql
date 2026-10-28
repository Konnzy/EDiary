ALTER TABLE user_notification DROP CONSTRAINT user_notification_email_kind_check;
ALTER TABLE user_notification ADD CONSTRAINT user_notification_email_kind_check
    CHECK (email_kind IN ('ABSENCE', 'CORRECTION', 'NOTICE'));

ALTER TABLE message ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX uq_message_sender_request ON message(sender_id, client_request_id);
CREATE INDEX idx_message_inbox ON message(receiver_id, message_created_at DESC, message_id DESC);
CREATE INDEX idx_message_sent ON message(sender_id, message_created_at DESC, message_id DESC);
CREATE INDEX idx_message_unread ON message(receiver_id) WHERE NOT message_is_read;
