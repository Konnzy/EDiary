package com.ediary.backend.school.dto.response;

import java.util.List;

public record UserScheduleResponse(
    String academicYear,
    Integer week,
    String className,
    List<UserScheduleLessonResponse> lessons
) {
}
