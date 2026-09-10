package com.ediary.backend.registration.event;

public record RegistrationRejectedEvent(
    String email,
    String recipientName,
    String reason
) {
}
