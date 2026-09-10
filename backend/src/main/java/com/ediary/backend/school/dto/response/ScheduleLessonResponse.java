package com.ediary.backend.school.dto.response;

import com.ediary.backend.school.entity.Weekday;

public record ScheduleLessonResponse(
    Long id,
    Weekday weekday,
    Integer lessonNumber,
    Long subjectId,
    String subject,
    Long teacherId,
    String teacher,
    String room,
    String startTime,
    String endTime
) {
}
