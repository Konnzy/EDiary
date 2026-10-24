package com.ediary.backend.notification.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.Attendance;
import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.notification.entity.UserNotification;
import com.ediary.backend.notification.entity.UserNotificationSettings;
import com.ediary.backend.notification.repository.UserNotificationRepository;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceNotificationTest {
    private final UserNotificationRepository notifications = mock(UserNotificationRepository.class);
    private final UserNotificationSettingsRepository settings = mock(UserNotificationSettingsRepository.class);
    private final StudentParentLinkRepository parents = mock(StudentParentLinkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AttendanceRepository attendance = mock(AttendanceRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-24T09:00:00Z"));
    private final AtomicReference<UserNotification> stored = new AtomicReference<>();
    private final Map<Long, UserNotification> parentNotices = new HashMap<>();
    private UserNotificationService service;
    private NotificationEmailSender sender;
    private StudentProfile student;
    private Lesson lesson;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenAnswer(call -> time.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        service = new UserNotificationService(notifications, settings, parents, users, clock);
        sender = new NotificationEmailSender(notifications, settings, attendance, students, lessons, service, email, clock);
        var user = new AppUser();
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmail("test@example.invalid");
        student = new StudentProfile();
        student.setId(2L);
        student.setUser(user);
        student.setSurname("Test");
        student.setName("Student");
        var subject = new Subject();
        subject.setTitle("Math");
        var schedule = new ScheduleEntry();
        schedule.setSubject(subject);
        lesson = new Lesson();
        lesson.setId(3L);
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setDate(LocalDate.of(2026, 10, 24));
        lesson.setScheduleEntry(schedule);
        when(notifications.findByRecipient_IdAndEventKey(anyLong(), eq("attendance:2:3")))
            .thenAnswer(call -> Optional.ofNullable(call.<Long>getArgument(0).equals(1L) ? stored.get() : parentNotices.get(call.<Long>getArgument(0))));
        when(notifications.save(any())).thenAnswer(call -> {
            UserNotification item = call.getArgument(0);
            item.setId(4L);
            if (item.getRecipient().getId().equals(1L)) stored.set(item);
            else parentNotices.put(item.getRecipient().getId(), item);
            return item;
        });
        when(notifications.findForDelivery(4L)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(students.findById(2L)).thenReturn(Optional.of(student));
        when(lessons.findById(3L)).thenReturn(Optional.of(lesson));
    }

    private void absence() {
        service.attendanceChanged(student, lesson, null, AttendanceStatus.ABSENT);
    }

    private void current(AttendanceStatus status) {
        var record = new Attendance();
        record.setStatus(status);
        when(attendance.findByStudentIdAndLessonId(2L, 3L)).thenReturn(Optional.of(record));
    }

    @Test
    void queuesOneMinuteAndSameStatusDoesNotDuplicateOrResetDelay() {
        absence();
        var due = LocalDateTime.of(2026, 10, 24, 9, 1);
        assertThat(stored.get().getEmailDueAt()).isEqualTo(due);
        time.set(time.get().plusSeconds(20));
        service.attendanceChanged(student, lesson, AttendanceStatus.ABSENT, AttendanceStatus.ABSENT);
        assertThat(stored.get().getEmailDueAt()).isEqualTo(due);
        verify(notifications, times(1)).save(any());
        sender.deliver(4L);
        verifyNoInteractions(email);
    }

    @Test
    void unreadUnsentAbsenceIsWithdrawnAndMailCancelled() {
        absence();
        service.attendanceChanged(student, lesson, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT);
        assertThat(stored.get().isWithdrawn()).isTrue();
        assertThat(stored.get().getEmailDueAt()).isNull();
        assertThat(stored.get().getEmailKind()).isNull();
    }

    @Test
    void readAbsenceBecomesUnreadCorrectionAndRemainsAfterFurtherClarification() {
        absence();
        stored.get().setReadAt(LocalDateTime.now(clock));
        service.attendanceChanged(student, lesson, AttendanceStatus.ABSENT, AttendanceStatus.EXCUSED_ABSENCE);
        service.attendanceChanged(student, lesson, AttendanceStatus.EXCUSED_ABSENCE, AttendanceStatus.PRESENT);
        assertThat(stored.get().isWithdrawn()).isFalse();
        assertThat(stored.get().getReadAt()).isNull();
        assertThat(stored.get().getBody()).contains("присутній");
        assertThat(stored.get().getEmailKind()).isNull();
    }

    @Test
    void rereadsJournalBeforeSendingEvenIfNotificationUpdateWasMissed() {
        absence();
        time.set(time.get().plusSeconds(61));
        current(AttendanceStatus.PRESENT);
        sender.deliver(4L);
        assertThat(stored.get().isWithdrawn()).isTrue();
        assertThat(stored.get().getEmailKind()).isNull();
        verifyNoInteractions(email);
    }

    @Test
    void sendsAbsenceOnceAndSendsCorrectionOnlyAfterPreviousMail() {
        absence();
        time.set(time.get().plusSeconds(61));
        current(AttendanceStatus.ABSENT);
        when(email.isEnabled()).thenReturn(true);
        when(email.sendText(anyString(), anyString(), anyString())).thenReturn(true);
        sender.deliver(4L);
        sender.deliver(4L);
        assertThat(stored.get().isLastEmailedAbsent()).isTrue();
        verify(email, times(1)).sendText(anyString(), anyString(), anyString());
        service.attendanceChanged(student, lesson, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT);
        current(AttendanceStatus.PRESENT);
        assertThat(stored.get().getEmailKind()).isEqualTo("CORRECTION");
        assertThat(stored.get().isWithdrawn()).isFalse();
        sender.deliver(4L);
        sender.deliver(4L);
        verify(email, times(2)).sendText(anyString(), anyString(), anyString());
        assertThat(stored.get().isLastEmailedAbsent()).isFalse();
    }

    @Test
    void changedEmailPreferenceCancelsQueuedMailWithoutHidingInAppNotice() {
        absence();
        var preference = new UserNotificationSettings();
        preference.setAttendanceEmailEnabled(false);
        when(settings.findByUser_Id(1L)).thenReturn(Optional.of(preference));
        time.set(time.get().plusSeconds(61));
        current(AttendanceStatus.ABSENT);
        sender.deliver(4L);
        assertThat(stored.get().getEmailDueAt()).isNull();
        assertThat(stored.get().isVisibleInApp()).isTrue();
        verifyNoInteractions(email);
    }

    @Test
    void smtpFailureRetriesAndDisabledSmtpDoesNotCountAsDelivery() {
        absence();
        time.set(time.get().plusSeconds(61));
        current(AttendanceStatus.ABSENT);
        sender.deliver(4L);
        assertThat(stored.get().getEmailAttempts()).isZero();
        assertThat(stored.get().isLastEmailedAbsent()).isFalse();
        time.set(time.get().plusSeconds(61));
        when(email.isEnabled()).thenReturn(true);
        sender.deliver(4L);
        assertThat(stored.get().getEmailAttempts()).isEqualTo(1);
        assertThat(stored.get().getEmailDueAt()).isAfter(LocalDateTime.now(clock));
        assertThat(stored.get().isLastEmailedAbsent()).isFalse();
    }

    @Test
    void dismissIsOwnedByRecipientAndDoesNotCancelEmail() {
        absence();
        when(users.findByLoginIgnoreCase("student")).thenReturn(Optional.of(student.getUser()));
        when(notifications.findByIdAndRecipient_Id(4L, 1L)).thenReturn(Optional.of(stored.get()));
        service.dismiss("student", 4L);
        assertThat(stored.get().getDismissedAt()).isNotNull();
        assertThat(stored.get().getEmailKind()).isEqualTo("ABSENCE");
        assertThatThrownBy(() -> service.dismiss("student", 5L)).isInstanceOf(ApplicationException.class);
    }

    @Test
    void mailAndInAppChannelsAreIndependent() {
        var preference = new UserNotificationSettings();
        preference.setAttendanceEmailEnabled(false);
        when(settings.findByUser_Id(1L)).thenReturn(Optional.of(preference));
        absence();
        assertThat(stored.get().isVisibleInApp()).isTrue();
        assertThat(stored.get().getEmailKind()).isNull();
        preference.setAttendanceEmailEnabled(true);
        preference.getInApp().put("absence", false);
        service.attendanceChanged(student, lesson, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT);
        service.attendanceChanged(student, lesson, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT);
        assertThat(stored.get().isVisibleInApp()).isFalse();
        assertThat(stored.get().getEmailKind()).isEqualTo("ABSENCE");
    }

    @Test
    void studentAndParentReadStatesAreIndependent() {
        var parentUser = new AppUser();
        parentUser.setId(5L);
        parentUser.setStatus(UserStatus.ACTIVE);
        var parent = new ParentProfile();
        parent.setId(6L);
        parent.setUser(parentUser);
        var link = new StudentParentLink(student, parent, true);
        when(parents.findAllByStudent_IdOrderByPrimaryContactDesc(2L)).thenReturn(List.of(link));
        absence();
        stored.get().setReadAt(LocalDateTime.now(clock));
        service.attendanceChanged(student, lesson, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT);
        assertThat(stored.get().isWithdrawn()).isFalse();
        assertThat(parentNotices.get(5L).isWithdrawn()).isTrue();
        assertThat(stored.get().getEventKey()).isEqualTo(parentNotices.get(5L).getEventKey());
    }
}
