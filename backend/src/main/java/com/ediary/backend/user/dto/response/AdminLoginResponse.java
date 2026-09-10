package com.ediary.backend.user.dto.response;

import com.ediary.backend.user.entity.UserRole;

public record AdminLoginResponse(
    Long userId,
    String login,
    UserRole role,
    String displayName
) {
}
