package com.ediary.backend.user.auth.dto;

import com.ediary.backend.user.entity.UserRole;

public record CurrentSessionResponse(
    Long userId,
    String login,
    UserRole role,
    boolean mustChangePassword
) {
}
