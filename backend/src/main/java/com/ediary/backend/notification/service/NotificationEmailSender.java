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
    @org.springframework.beans.factory.annotation.Value("${app.password-reset.frontend-url:http://127.0.0.1:5173}")
    private String frontendUrl = "http://127.0.0.1:5173";

    @Transactional
    public void deliver(Long id) {
        var item = notifications.findForDelivery(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (item == null || item.getEmailDueAt() == null || item.getEmailDueAt().isAfter(now)) return;
        boolean absenceNotice = "ABSENCE".equals(item.getCategory());
        if (absenceNotice) {
            if (item.getAttendanceLessonId() == null) { item.setEmailDueAt(null); return; }
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
        }
        if (item.getEmailKind() == null || item.getEmailDueAt() == null || item.getEmailDueAt().isAfter(now)) return;
        var preference = settings.findByUser_Id(item.getRecipient().getId()).orElse(null);
        if (item.isWithdrawn() || item.getRecipient().getStatus() != UserStatus.ACTIVE || !NotificationChannels.email(preference, item.getCategory())) {
            item.setEmailKind(null);
            item.setEmailDueAt(null);
            log.info("Notification email cancelled notificationId={} recipientId={} reason=preferences_or_account", id, item.getRecipient().getId());
            return;
        }
        if (!email.isEnabled()) {
            item.setEmailDueAt(now.plusMinutes(1));
            log.debug("Notification email deferred notificationId={} reason=mail_disabled", id);
            return;
        }
        String kind = item.getEmailKind();
        if (absenceNotice ? !"ABSENCE".equals(kind) && !"CORRECTION".equals(kind) : !"NOTICE".equals(kind)) return;
        String source=item.getEmailBody()==null?item.getBody():item.getEmailBody();
        String title=NotificationText.title(item.getCategory(),source,item.getTitle());
        String content=NotificationText.concise(item.getCategory(),source);
        if(item.getCategory().equals("GRADE"))content=content.lines().filter(line->!line.equals(title)).collect(java.util.stream.Collectors.joining("\n"));
        String action=switch(item.getCategory()) {
            case "SCHEDULE", "CLASS_SCHEDULE" -> "Переглянути розклад";
            case "MESSAGE" -> "Прочитати повідомлення";
            case "HOMEWORK", "HOMEWORK_DEADLINE" -> "Переглянути домашнє завдання";
            case "CLASS_ROSTER", "PROLONGED_ABSENCE" -> "Переглянути дані класу";
            default -> "Переглянути деталі";
        };
        String letter=title+"\n\n"+content
            +"\n\n"+action+":\n"+frontendUrl.replaceAll("/+$", "")+"/"+item.getTarget();
        if (email.sendText(item.getRecipient().getEmail(), title, letter)) {
            if (absenceNotice) item.setLastEmailedAbsent("ABSENCE".equals(kind));
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
            log.warn("Notification email failed notificationId={} kind={} attempt={} exhausted={}", id, kind, attempts, attempts >= 5);
        }
    }
}
