package com.ediary.backend.notification.service;

import com.ediary.backend.journal.entity.HomeworkStatus;
import com.ediary.backend.journal.repository.HomeworkRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notifications.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class HomeworkDeadlineReminder {
    private final HomeworkRepository homeworkRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final StudentProfileRepository students;
    private final UserNotificationService notifications;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() { sendTomorrowReminders(); }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Kyiv")
    @Transactional
    public void sendTomorrowReminders() {
        com.ediary.backend.common.logging.DiagnosticContext.job("homework-reminders", () -> {
            long started = System.nanoTime();
            log.info("event=homework_reminders.started");
            try {
                int count = sendReminders();
                com.ediary.backend.common.logging.DiagnosticContext.afterCommit(() ->
                    log.info("event=homework_reminders.committed considered={} durationMs={}", count, (System.nanoTime() - started) / 1_000_000));
            } catch (RuntimeException error) {
                throw new IllegalStateException("event=homework_reminders.failed requestId=" + org.slf4j.MDC.get("requestId"),
                    com.ediary.backend.common.logging.SafeExceptions.forLogging(error));
            }
        });
    }

    private int sendReminders() {
        int count = 0;
        LocalDate deadline = LocalDate.now(clock).plusDays(1);
        for (var homework : homeworkRepository.findAllByDeadline(deadline)) {
            var lesson = homework.getLesson();
            var schoolClass = lesson.getScheduleEntry().getSchoolClass();
            for (var student : students.findAllBySchoolClassIdOrderBySurnameAscNameAsc(schoolClass.getId())) {
                var status = studentHomeworkRepository.findByStudentIdAndHomeworkId(student.getId(), homework.getId())
                    .map(value -> value.getStatus()).orElse(null);
                if (status == HomeworkStatus.COMPLETED || status == HomeworkStatus.SUBMITTED_LATE) continue;
                notifications.forStudent(student, "HOMEWORK_DEADLINE", "Дедлайн домашнього завдання",
                    "Нагадування: завтра спливає термін виконання.\n\n"+NotificationText.homework(homework),
                    "homework-deadline:" + homework.getId() + ":" + deadline);
                count++;
            }
        }
        return count;
    }
}
