package com.ediary.backend.user.admin.event;

public record AdminUserCreatedEvent(
    String email,
    String recipientName,
    String login,
    String temporaryPassword,
    String parentLinkCode
) {
}
