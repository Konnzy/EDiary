package com.ediary.backend.registration.event;

import java.util.function.Consumer;

public record RegistrationApprovedEvent(
    String email,
    String recipientName,
    String login,
    String temporaryPassword,
    String parentLinkCode,
    Consumer<Boolean> deliveryResult
) {
    public RegistrationApprovedEvent(String email, String recipientName, String login,
                                     String temporaryPassword, String parentLinkCode) {
        this(email, recipientName, login, temporaryPassword, parentLinkCode, sent -> {});
    }
}
