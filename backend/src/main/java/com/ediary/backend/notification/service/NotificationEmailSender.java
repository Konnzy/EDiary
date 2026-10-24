package com.ediary.backend.notification.service;

import com.ediary.backend.notification.repository.UserNotificationRepository;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.entity.UserStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailSender {
    private final UserNotificationRepository notifications;
    private final UserNotificationSettingsRepository settings;
    private final AttendanceRepository attendance;
    private final StudentProfileRepository students;
    private final LessonRepository lessons;
    private final UserNotificationService notificationService;
    private final EmailService email;
    private final Clock clock;

    @Transactional
    public void deliver(Long id) {
        var item = notifications.findForDelivery(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (item == null || item.getEmailDueAt() == null || item.getEmailDueAt().isAfter(now)) return;
        // This queue only delivers absence notices and their corrections.
        if (!"ABSENCE".equals(item.getCategory()) || item.getAttendanceLessonId() == null) return;
        var lesson = lessons.findById(item.getAttendanceLessonId()).orElse(null);
        var student = students.findById(item.getAttendanceStudentId()).orElse(null);
        if (lesson == null || student == null) {
            item.setEmailKind(null);
            item.setEmailDueAt(null);
            return;
        }
        var current = lesson.getStatus() == LessonStatus.CANCELLED ? null : attendance
            .findByStudentIdAndLessonId(student.getId(), lesson.getId()).map(value -> value.getStatus()).orElse(null);
        notificationService.reconcileAttendance(item, student, lesson, current);
        if (item.getEmailKind() == null || item.getEmailDueAt() == null || item.getEmailDueAt().isAfter(now)) return;
        var preference = settings.findByUser_Id(item.getRecipient().getId()).orElse(null);
        if (item.getRecipient().getStatus() != UserStatus.ACTIVE || !NotificationChannels.email(preference, "ABSENCE")) {
            item.setEmailKind(null);
            item.setEmailDueAt(null);
            log.info("Attendance email cancelled notificationId={} recipientId={} reason=preferences_or_account", id, item.getRecipient().getId());
            return;
        }
        if (!email.isEnabled()) {
            item.setEmailDueAt(now.plusMinutes(1));
            log.debug("Attendance email deferred notificationId={} reason=mail_disabled", id);
            return;
        }
        String kind = item.getEmailKind();
        if (!"ABSENCE".equals(kind) && !"CORRECTION".equals(kind)) return;
        if (email.sendText(item.getRecipient().getEmail(), item.getTitle(), item.getBody())) {
            item.setLastEmailedAbsent("ABSENCE".equals(kind));
            item.setEmailKind(null);
            item.setEmailDueAt(null);
            item.setEmailSentAt(now);
            item.setEmailAttempts(0);
            Long recipientId = item.getRecipient().getId();
            com.ediary.backend.common.logging.DiagnosticContext.afterCommit(() ->
                log.info("event=notification_email.accepted notificationId={} recipientId={} kind={}", id, recipientId, kind));
        } else {
            int attempts = item.getEmailAttempts() + 1;
            item.setEmailAttempts(attempts);
            item.setEmailDueAt(attempts >= 5 ? null : now.plusSeconds(30L << attempts));
            log.warn("Attendance email failed notificationId={} kind={} attempt={} exhausted={}", id, kind, attempts, attempts >= 5);
        }
    }
}
