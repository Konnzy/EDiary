package com.ediary.backend.common.dashboard.dto;

public record AdminDashboardStatsResponse(
    long students,
    long activeStudents,
    long teachers,
    long activeTeachers,
    long parents,
    long activeParents,
    long activeUsers,
    long totalUsers,
    long pendingRequests
) {
}
