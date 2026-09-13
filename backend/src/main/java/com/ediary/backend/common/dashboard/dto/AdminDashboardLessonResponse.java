package com.ediary.backend.common.dashboard.dto;

public record AdminDashboardLessonResponse(
    Long id,
    String startTime,
    String subject,
    String className,
    String teacher,
    String room
) {
}
