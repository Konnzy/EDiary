package com.ediary.backend.school.dto.response;

import com.ediary.backend.school.entity.Weekday;

public record UserScheduleLessonResponse(
    Long id,
    Weekday weekday,
    Integer lessonNumber,
    String subject,
    String teacher,
    String className,
    String room,
    String startTime,
    String endTime
) {
}
