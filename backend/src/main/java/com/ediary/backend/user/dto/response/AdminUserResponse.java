package com.ediary.backend.user.dto.response;

import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import java.time.LocalDateTime;

public record AdminUserResponse(
    Long id,
    String login,
    String email,
    UserRole role,
    UserStatus status,
    boolean mustChangePassword,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
