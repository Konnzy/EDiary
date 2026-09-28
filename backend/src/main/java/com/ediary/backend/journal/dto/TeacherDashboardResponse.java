package com.ediary.backend.journal.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TeacherDashboardResponse(
    int lessonsWithoutTopicCount,
    List<LessonWithoutTopic> lessonsWithoutTopic,
    List<RecentJournalChange> recentChanges
) {
    public record LessonWithoutTopic(
        Long classId,
        Long subjectId,
        String className,
        String subjectName,
        LocalDate date,
        Integer lessonNumber
    ) { }

    public record RecentJournalChange(
        Long lessonId,
        Long classId,
        Long subjectId,
        String className,
        String subjectName,
        LocalDate lessonDate,
        Integer lessonNumber,
        String topic,
        LocalDateTime updatedAt
    ) { }
}
