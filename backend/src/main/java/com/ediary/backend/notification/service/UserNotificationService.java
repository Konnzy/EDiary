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
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNotificationService {
    private final UserNotificationRepository notifications;
    private final UserNotificationSettingsRepository settings;
    private final StudentParentLinkRepository parentLinks;
    private final UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserNotificationFeedResponse feed(String login) {
        AppUser user = requireUser(login);
        return new UserNotificationFeedResponse(
            notifications.countByRecipient_IdAndReadAtIsNull(user.getId()),
            notifications.findTop20ByRecipient_IdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .map(item -> new UserNotificationFeedResponse.Item(item.getId(), item.getCategory(),
                    item.getTitle(), item.getBody(), item.getTarget(), item.getCreatedAt(),
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

    public void forStudent(StudentProfile student, String category, String title, String body, String eventKey) {
        String page = "SCHEDULE".equals(category) ? "schedule" : "subjects";
        send(student.getUser(), category, title, body, "student/" + page, eventKey);
        parentLinks.findAllByStudent_IdOrderByPrimaryContactDesc(student.getId()).forEach(link ->
            send(link.getParent().getUser(), category, title,
                student.getSurname() + " " + student.getName() + " · " + body,
                "parent/" + page + "?childId=" + student.getId(), eventKey));
    }

    public void forTeacher(TeacherProfile teacher, String category, String title, String body,
                           String target, String eventKey) {
        if (teacher != null) send(teacher.getUser(), category, title, body, target, eventKey);
    }

    private void send(AppUser recipient, String category, String title, String body,
                      String target, String eventKey) {
        if (recipient.getStatus() != UserStatus.ACTIVE || !enabled(recipient, category)) return;
        if (eventKey != null && notifications.existsByRecipient_IdAndEventKey(recipient.getId(), eventKey)) return;
        UserNotification item = new UserNotification();
        item.setRecipient(recipient);
        item.setCategory(category);
        item.setTitle(title);
        item.setBody(body);
        item.setTarget(target);
        item.setEventKey(eventKey);
        item.setCreatedAt(LocalDateTime.now(clock));
        notifications.save(item);
    }

    private boolean enabled(AppUser recipient, String category) {
        UserNotificationSettings preference = settings.findByUser_Id(recipient.getId()).orElse(null);
        if (preference == null) return true;
        return switch (category) {
            case "GRADE" -> preference.isGradeEmailEnabled();
            case "HOMEWORK" -> preference.isHomeworkEmailEnabled();
            case "HOMEWORK_DEADLINE" -> preference.isHomeworkDeadlineReminderEmailEnabled();
            case "ABSENCE" -> preference.isAttendanceEmailEnabled();
            case "SCHEDULE" -> preference.isScheduleChangeEmailEnabled();
            case "CLASS_SCHEDULE" -> preference.isClassScheduleChangeEmailEnabled();
            case "PROLONGED_ABSENCE" -> preference.isProlongedAbsenceEmailEnabled();
            case "CLASS_ROSTER" -> preference.isClassRosterChangeEmailEnabled();
            default -> false;
        };
    }

    private AppUser requireUser(String login) {
        return users.findByLoginIgnoreCase(login)
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND", "Користувача не знайдено"));
    }
}
