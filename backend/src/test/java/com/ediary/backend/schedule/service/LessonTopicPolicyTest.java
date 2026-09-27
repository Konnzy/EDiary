package com.ediary.backend.schedule.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class LessonTopicPolicyTest {
    private final LocalDate today = LocalDate.of(2026, 9, 27);

    @Test
    void allowsFutureLessonWithoutTopicOnlyWithoutJournalRecords() {
        Lesson lesson = lesson(today.plusDays(1));
        assertThatCode(() -> LessonTopicPolicy.validate(lesson, null, today, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> LessonTopicPolicy.validate(lesson, " ", today, true))
            .isInstanceOf(ApplicationException.class);
    }

    @Test
    void requiresTopicForTodayPastAndCompletedLessons() {
        for (LocalDate date : new LocalDate[]{today, today.minusDays(1)}) {
            assertThatThrownBy(() -> LessonTopicPolicy.validate(lesson(date), null, today, false))
                .isInstanceOf(ApplicationException.class);
        }
        Lesson completed = lesson(today.plusDays(1));
        completed.setStatus(LessonStatus.COMPLETED);
        assertThatThrownBy(() -> LessonTopicPolicy.validate(completed, null, today, false))
            .isInstanceOf(ApplicationException.class);
    }

    private Lesson lesson(LocalDate date) {
        Lesson lesson = new Lesson();
        lesson.setDate(date);
        lesson.setStatus(LessonStatus.PLANNED);
        return lesson;
    }
}
