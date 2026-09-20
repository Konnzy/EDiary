package com.ediary.backend.user.admin.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
    AdminDashboardStatsResponse stats,
    List<AdminDashboardRequestResponse> recentRequests,
    List<AdminActivityResponse> recentAdminActions,
    List<AdminDashboardLessonResponse> todayLessons,
    LocalDate today,
    String currentAcademicYear,
    Integer currentWeek
) {
}
