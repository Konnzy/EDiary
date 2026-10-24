package com.ediary.backend.common.audit;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "domain_audit")
@Getter
@NoArgsConstructor
public class AuditEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "actor_role", nullable = false, length = 24)
    private String actorRole;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;
    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;
    @Column(name = "request_id", length = 36)
    private String requestId;
    @Column(name = "before_data", nullable = false, columnDefinition = "text")
    private String beforeData;
    @Column(name = "after_data", nullable = false, columnDefinition = "text")
    private String afterData;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AuditEntry(Long actorUserId, String actorRole, String action, String targetType, String targetId,
        String requestId, String beforeData, String afterData, Instant occurredAt) {
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.occurredAt = occurredAt;
    }
}
