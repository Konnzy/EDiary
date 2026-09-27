package com.ediary.backend.schedule.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

public final class LessonTopicPolicy {
    private LessonTopicPolicy() {}

    public static void requireTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "LESSON_TOPIC_REQUIRED",
                "Вкажіть тему уроку перед внесенням оцінок, відвідування або домашнього завдання");
        }
    }

    public static void validate(Lesson lesson, String topic, LocalDate today, boolean hasJournalRecords) {
        if (lesson.getStatus() == LessonStatus.COMPLETED
            || (lesson.getStatus() != LessonStatus.CANCELLED && !lesson.getDate().isAfter(today))
            || hasJournalRecords) {
            requireTopic(topic);
        }
    }
}
