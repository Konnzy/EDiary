package com.ediary.backend.common.dashboard.dto;

import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.user.entity.UserRole;
import java.time.LocalDateTime;

public record AdminDashboardRequestResponse(
    Long id,
    String fullName,
    String email,
    UserRole role,
    String details,
    RegistrationStatus status,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt
) {
}
