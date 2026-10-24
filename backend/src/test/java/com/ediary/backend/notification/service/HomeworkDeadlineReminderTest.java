package com.ediary.backend.notification.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ediary.backend.journal.repository.HomeworkRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeworkDeadlineReminderTest {
    @Test
    void delegatesOneSafeFailureToSchedulerAndRestoresContext() {
        var repository = mock(HomeworkRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
        when(repository.findAllByDeadline(LocalDate.of(2026, 10, 25)))
            .thenThrow(new IllegalStateException("password=secret"));
        var reminder = new HomeworkDeadlineReminder(repository, mock(StudentHomeworkRepository.class),
            mock(StudentProfileRepository.class), mock(UserNotificationService.class), clock);
        var logger = (Logger) LoggerFactory.getLogger(HomeworkDeadlineReminder.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "caller");
        try {
            assertThatThrownBy(reminder::sendTomorrowReminders)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageMatching("event=homework_reminders.failed requestId=[a-f0-9-]{36}")
                .satisfies(error -> assertThat(error.getCause().getMessage()).doesNotContain("secret"));
            assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
            assertThat(MDC.get("requestId")).isEqualTo("caller");
            assertThat(MDC.get("job")).isNull();
        } finally {
            logger.detachAppender(appender);
            MDC.clear();
        }
    }
}
