package com.ediary.backend.user.profile.dto;

import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import java.time.LocalDateTime;

public record UserProfileResponse(
    Long id,
    String login,
    String email,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
