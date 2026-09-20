package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.schedule.entity.LessonStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LessonResponse(
    Long id,
    Long scheduleId,
    LocalDate date,
    LessonStatus status,
    String topic,
    Long classId,
    String className,
    Long subjectId,
    String subject,
    Long teacherId,
    String teacher,
    Integer lessonNumber,
    String room,
    String startTime,
    String endTime,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
