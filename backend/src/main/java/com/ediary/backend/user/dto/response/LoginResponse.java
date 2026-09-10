package com.ediary.backend.user.dto.response;

import com.ediary.backend.user.entity.UserRole;

public record LoginResponse(
    Long userId,
    String login,
    UserRole role,
    boolean mustChangePassword
) {
}
