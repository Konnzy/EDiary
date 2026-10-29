CREATE TABLE admin_notification_dismissal (
    admin_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    request_id BIGINT NOT NULL REFERENCES registration_request(request_id) ON DELETE CASCADE,
    request_status VARCHAR(16) NOT NULL CHECK(request_status IN ('APPROVED','REJECTED')),
    dismissed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(admin_id,request_id,request_status)
);
