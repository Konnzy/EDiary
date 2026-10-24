CREATE TABLE domain_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT,
    actor_role VARCHAR(24) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(36),
    before_data TEXT NOT NULL,
    after_data TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_domain_audit_target ON domain_audit(target_type, target_id, occurred_at DESC);
CREATE INDEX idx_domain_audit_actor ON domain_audit(actor_user_id, occurred_at DESC);
