package com.ediary.backend.registration.event;

public record RegistrationApprovedEvent(
    String email,
    String recipientName,
    String login,
    String temporaryPassword,
    String parentLinkCode
) {
}
