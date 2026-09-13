package com.ediary.backend.school.dto.response;

import java.util.List;

public record ScheduleOptionsResponse(
    List<String> academicYears,
    String currentAcademicYear,
    Integer currentWeek
) {
}
