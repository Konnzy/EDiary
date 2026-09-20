package com.ediary.backend.user.auth.dto;

import com.ediary.backend.user.entity.UserRole;

public record LoginResponse(
    Long userId,
    String login,
    UserRole role,
    boolean mustChangePassword
) {
}
