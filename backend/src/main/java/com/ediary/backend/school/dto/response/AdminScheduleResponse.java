package com.ediary.backend.school.dto.response;

import java.util.List;

public record AdminScheduleResponse(
    Long classId,
    String academicYear,
    Integer week,
    List<ScheduleLessonResponse> lessons
) {
}
