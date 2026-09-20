package com.ediary.backend.user.admin.event;

public record AdminUserPasswordResetEvent(
    String email,
    String login,
    String temporaryPassword
) {
}
