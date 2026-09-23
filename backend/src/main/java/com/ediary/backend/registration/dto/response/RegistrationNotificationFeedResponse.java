package com.ediary.backend.registration.dto.response;

import java.util.List;

public record RegistrationNotificationFeedResponse(
    long pendingCount,
    List<RegistrationRequestResponse> items
) {
}
