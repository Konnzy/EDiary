package com.ediary.backend.user.auth.dto;

import com.ediary.backend.user.entity.UserRole;

public record AdminLoginResponse(
    Long userId,
    String login,
    UserRole role,
    String displayName
) {
}
