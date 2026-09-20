package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.schedule.entity.Weekday;

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
