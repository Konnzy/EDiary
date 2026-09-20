package com.ediary.backend.user.session.entity;

import com.ediary.backend.user.entity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_session")
@Getter
@Setter
@NoArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(name = "session_ip_address", length = 45)
    private String ipAddress;

    @Column(name = "session_user_agent", length = 500)
    private String userAgent;

    @Column(name = "session_created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "session_last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "session_expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "session_revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "session_revoked_reason", length = 200)
    private String revokedReason;
}
