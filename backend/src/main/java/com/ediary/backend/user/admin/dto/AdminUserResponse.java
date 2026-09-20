package com.ediary.backend.user.admin.dto;

import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import java.time.LocalDateTime;

public record AdminUserResponse(
    Long id,
    String login,
    String firstName,
    String lastName,
    String patronymic,
    String email,
    String phone,
    UserRole role,
    UserStatus status,
    boolean mustChangePassword,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
