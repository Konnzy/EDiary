package com.ediary.backend.notification.entity;

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
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private AppUser receiver;

    @Column(name = "message_subject", nullable = false, length = 150)
    private String subject;

    @Column(name = "message_text", nullable = false, length = 1000)
    private String text;

    @Column(name = "message_created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "message_is_read", nullable = false)
    private boolean read;

    @Column(name = "message_is_important", nullable = false)
    private boolean important;

    @Column(name = "client_request_id")
    private java.util.UUID clientRequestId;
}
