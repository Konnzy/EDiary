package com.ediary.backend.notification.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.notification.dto.UserNotificationFeedResponse;
import com.ediary.backend.notification.entity.UserNotification;
import com.ediary.backend.notification.entity.UserNotificationSettings;
import com.ediary.backend.notification.repository.UserNotificationRepository;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationService {
    private final UserNotificationRepository notifications;
    private final UserNotificationSettingsRepository settings;
    private final StudentParentLinkRepository parentLinks;
    private final UserRepository users;
    private final Clock clock;

    @Value("${app.notifications.absence-email-delay:PT1M}")
    private Duration absenceEmailDelay = Duration.ofMinutes(1);

    @Transactional(readOnly = true)
    public UserNotificationFeedResponse feed(String login) {
        AppUser user = requireUser(login);
        return new UserNotificationFeedResponse(
            notifications.countByRecipient_IdAndReadAtIsNullAndDismissedAtIsNullAndWithdrawnFalseAndVisibleInAppTrue(user.getId()),
            notifications.findTop20ByRecipient_IdAndDismissedAtIsNullAndWithdrawnFalseAndVisibleInAppTrueOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .map(item -> new UserNotificationFeedResponse.Item(item.getId(), item.getCategory(),
                    NotificationText.previewTitle(item.getCategory(),item.getEmailBody()==null?item.getBody():item.getEmailBody(),item.getTitle()), NotificationText.preview(item.getCategory(),item.getEmailBody()==null?item.getBody():item.getEmailBody(),user.getRole()==com.ediary.backend.user.entity.UserRole.PARENT), item.getTarget(), item.getCreatedAt(),
                    item.getReadAt() != null)).toList()
        );
    }

    @Transactional
    public void markRead(String login, Long id) {
        AppUser user = requireUser(login);
        UserNotification item = notifications.findByIdAndRecipient_Id(id, user.getId())
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                "NOTIFICATION_NOT_FOUND", "Сповіщення не знайдено"));
        if (item.getReadAt() == null) item.setReadAt(LocalDateTime.now(clock));
    }

    @Transactional
    public void markAllRead(String login) {
        notifications.markAllRead(requireUser(login).getId());
    }

    @Transactional
    public void dismiss(String login, Long id) {
        AppUser user = requireUser(login);
        UserNotification item = notifications.findByIdAndRecipient_Id(id, user.getId())
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                "NOTIFICATION_NOT_FOUND", "Сповіщення не знайдено"));
        if (item.getDismissedAt() == null) {
            item.setDismissedAt(LocalDateTime.now(clock));
            com.ediary.backend.common.logging.DiagnosticContext.afterCommit(() -> log.info("event=notification.dismissed notificationId={} recipientId={}", id, user.getId()));
        }
    }

    @Transactional
    public void dismissAll(String login) {
        var user=requireUser(login);
        int count=notifications.dismissAll(user.getId(),LocalDateTime.now(clock));
        com.ediary.backend.common.logging.DiagnosticContext.afterCommit(()->log.info("event=notification.cleared recipientId={} count={}",user.getId(),count));
    }

    @Transactional
    public void forStudent(StudentProfile student, String category, String title, String body, String eventKey) {
        String page = "SCHEDULE".equals(category) ? "schedule" : "subjects";
        String context="Учень: "+student.getSurname()+" "+student.getName()
            +(student.getPatronymic()==null?"":" "+student.getPatronymic())
            +(student.getSchoolClass()==null?"":"\nКлас: "+student.getSchoolClass().getDisplayName())+"\n\n";
        send(student.getUser(), category, title, context+body, "student/" + page, eventKey);
        parentLinks.findAllByStudent_IdOrderByPrimaryContactDesc(student.getId()).forEach(link ->
            send(link.getParent().getUser(), category, title,
                context + body,
                "parent/" + page + "?childId=" + student.getId(), eventKey));
    }

    @Transactional
    public void forTeacher(TeacherProfile teacher, String category, String title, String body,
                           String target, String eventKey) {
        if (teacher != null) send(teacher.getUser(), category, title, body, target, eventKey);
    }

    private void send(AppUser recipient, String category, String title, String body,
                      String target, String eventKey) {
        if (recipient.getStatus() != UserStatus.ACTIVE) return;
        var preference = settings.findByUser_Id(recipient.getId()).orElse(null);
        if (!NotificationChannels.inApp(preference, category)) return;
        if (eventKey != null && notifications.existsByRecipient_IdAndEventKey(recipient.getId(), eventKey)) {
            log.debug("Duplicate notification skipped recipientId={} category={}", recipient.getId(), category);
            return;
        }
        UserNotification item = new UserNotification();
        item.setRecipient(recipient);
        item.setCategory(category);
        item.setTitle(title);
        item.setBody(NotificationText.preview(category,body));
        item.setEmailBody(body);
        item.setTarget(target);
        item.setEventKey(eventKey);
        item.setCreatedAt(LocalDateTime.now(clock));
        if (NotificationChannels.email(preference, category)) {
            item.setEmailKind("NOTICE");
            item.setEmailDueAt(LocalDateTime.now(clock));
        }
        notifications.save(item);
        com.ediary.backend.common.logging.DiagnosticContext.afterCommit(() -> log.info("event=notification.created recipientId={} category={}", recipient.getId(), category));
    }

    @Transactional
    public void messageReceived(AppUser recipient, Long messageId, String senderName, String subject) {
        send(recipient, "MESSAGE", "Нове повідомлення", "Відправник: " + senderName + "\nТема: " + subject + "\n\nВам надійшло особисте повідомлення. Повний текст доступний у застосунку.",
            recipient.getRole().name().toLowerCase(java.util.Locale.ROOT) + "/messages?messageId=" + messageId,
            "message:" + messageId);
    }

    @Transactional
    public void messageRead(Long recipientId, Long messageId) {
        notifications.markEventRead(recipientId,"message:" + messageId);
    }

    @Transactional
    public void attendanceChanged(StudentProfile student, Lesson lesson, AttendanceStatus previous, AttendanceStatus current) {
        if (previous == current) return;
        updateAttendanceForRecipient(student.getUser(), student, lesson, current);
        parentLinks.findAllByStudent_IdOrderByPrimaryContactDesc(student.getId()).forEach(link ->
            updateAttendanceForRecipient(link.getParent().getUser(), student, lesson, current));
    }

    private void updateAttendanceForRecipient(AppUser recipient, StudentProfile student, Lesson lesson, AttendanceStatus current) {
        String key = "attendance:" + student.getId() + ":" + lesson.getId();
        UserNotification item = notifications.findByRecipient_IdAndEventKey(recipient.getId(), key).orElse(null);
        if (item == null) {
            if (current != AttendanceStatus.ABSENT || recipient.getStatus() != UserStatus.ACTIVE) return;
            item = new UserNotification();
            item.setRecipient(recipient);
            item.setCategory("ABSENCE");
            item.setEventKey(key);
            item.setAttendanceStudentId(student.getId());
            item.setAttendanceLessonId(lesson.getId());
            item.setTarget(recipient.getId().equals(student.getUser().getId())
                ? "student/subjects" : "parent/subjects?childId=" + student.getId());
        }
        reconcileAttendance(item, student, lesson, current);
        notifications.save(item);
    }

    public void reconcileAttendance(UserNotification item, StudentProfile student, Lesson lesson, AttendanceStatus current) {
        String state = current == null ? "UNRECORDED" : current.name();
        if (Objects.equals(state, item.getAttendanceStatus())) return;
        boolean seen = item.isAttendanceNoticeSeen() || item.getReadAt() != null || item.getDismissedAt() != null || item.isLastEmailedAbsent();
        boolean absent = current == AttendanceStatus.ABSENT;
        var preference = settings.findByUser_Id(item.getRecipient().getId()).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        String context = student.getSurname() + " " + student.getName() + " · "
            + lesson.getScheduleEntry().getSubject().getTitle() + " · "
            + lesson.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        item.setAttendanceStatus(state);
        item.setAttendanceNoticeSeen(!absent && seen);
        item.setTitle(absent ? "Відсутність на уроці" : "Відвідування уточнено");
        item.setBody(shortBody(context + (absent ? " · Учень відсутній." : " · " + attendanceDescription(current)
            + " Попередня позначка відсутності більше не актуальна.")));
        item.setEmailBody("Учень: "+student.getSurname()+" "+student.getName()
            +(student.getSchoolClass()==null?"":"\nКлас: "+student.getSchoolClass().getDisplayName())
            +"\n"+NotificationText.lesson(lesson)+"\n\n"
            +(absent?"Учня позначено відсутнім на цьому уроці.":"Попередню позначку відсутності скасовано. "+attendanceDescription(current)));
        item.setBody(NotificationText.preview("ABSENCE",item.getEmailBody()));
        item.setCreatedAt(now);
        item.setReadAt(null);
        item.setDismissedAt(null);
        item.setWithdrawn(!absent && !seen);
        item.setVisibleInApp(NotificationChannels.inApp(preference, "ABSENCE"));
        item.setEmailAttempts(0);
        String kind = absent == item.isLastEmailedAbsent() ? null : absent ? "ABSENCE" : "CORRECTION";
        if (!NotificationChannels.email(preference, "ABSENCE")) kind = null;
        item.setEmailKind(kind);
        item.setEmailDueAt(kind == null ? null : absent ? now.plus(absenceEmailDelay) : now);
        Long recipientId = item.getRecipient().getId();
        boolean withdrawn = item.isWithdrawn();
        String queuedKind = kind;
        com.ediary.backend.common.logging.DiagnosticContext.afterCommit(() -> log.info(
            "event=notification.attendance_reconciled recipientId={} studentId={} lessonId={} status={} withdrawn={} emailKind={}",
            recipientId, student.getId(), lesson.getId(), state, withdrawn, queuedKind));
    }

    private static String attendanceDescription(AttendanceStatus status) {
        if (status == null) return "Позначку відсутності скасовано.";
        return switch (status) {
            case PRESENT -> "Учень присутній.";
            case EXCUSED_ABSENCE -> "Відсутність із поважної причини.";
            case EXEMPTED -> "Учня звільнено від заняття.";
            case LATE -> "Учень прибув із запізненням.";
            case ABSENT -> "Учень відсутній.";
        };
    }

    private static String shortBody(String body) {
        return body.length() <= 500 ? body : body.substring(0, 497) + "...";
    }

    private AppUser requireUser(String login) {
        return users.findByLoginIgnoreCase(login)
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND", "Користувача не знайдено"));
    }
}
