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
@Table(name = "user_notification")
@Getter
@Setter
@NoArgsConstructor
public class UserNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @Column(name = "target", nullable = false, length = 500)
    private String target;

    @Column(name = "event_key", length = 160)
    private String eventKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;
    @Column(nullable = false)
    private boolean withdrawn;
    @Column(name = "visible_in_app", nullable = false)
    private boolean visibleInApp = true;
    @Column(name = "attendance_student_id")
    private Long attendanceStudentId;
    @Column(name = "attendance_lesson_id")
    private Long attendanceLessonId;
    @Column(name = "attendance_status", length = 32)
    private String attendanceStatus;
    @Column(name = "attendance_notice_seen", nullable = false)
    private boolean attendanceNoticeSeen;
    @Column(name = "last_emailed_absent", nullable = false)
    private boolean lastEmailedAbsent;
    @Column(name = "email_kind", length = 24)
    private String emailKind;
    @Column(name = "email_due_at")
    private LocalDateTime emailDueAt;
    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;
    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;
}
