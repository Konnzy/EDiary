package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.schedule.entity.Weekday;

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
