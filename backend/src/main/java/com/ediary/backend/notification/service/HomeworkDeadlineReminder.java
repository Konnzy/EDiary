package com.ediary.backend.notification.service;

import com.ediary.backend.journal.entity.HomeworkStatus;
import com.ediary.backend.journal.repository.HomeworkRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
        LocalDate deadline = LocalDate.now(clock).plusDays(1);
        for (var homework : homeworkRepository.findAllByDeadline(deadline)) {
            var lesson = homework.getLesson();
            var schoolClass = lesson.getScheduleEntry().getSchoolClass();
            for (var student : students.findAllBySchoolClassIdOrderBySurnameAscNameAsc(schoolClass.getId())) {
                var status = studentHomeworkRepository.findByStudentIdAndHomeworkId(student.getId(), homework.getId())
                    .map(value -> value.getStatus()).orElse(null);
                if (status == HomeworkStatus.COMPLETED || status == HomeworkStatus.SUBMITTED_LATE) continue;
                notifications.forStudent(student, "HOMEWORK_DEADLINE", "Дедлайн домашнього завдання",
                    lesson.getScheduleEntry().getSubject().getTitle() + " · виконати до " + deadline,
                    "homework-deadline:" + homework.getId() + ":" + deadline);
            }
        }
    }
}
