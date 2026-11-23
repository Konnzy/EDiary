package com.ediary.backend.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_notification_dismissal")
public class AdminNotificationDismissal {
    @EmbeddedId
    private Key id;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;

    protected AdminNotificationDismissal() {}

    @Embeddable
    public record Key(
        @Column(name = "admin_id") Long adminId,
        @Column(name = "request_id") Long requestId,
        @Column(name = "request_status", length = 16) String requestStatus
    ) {}
}
