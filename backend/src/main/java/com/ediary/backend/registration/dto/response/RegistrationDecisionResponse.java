package com.ediary.backend.registration.dto.response;

import com.ediary.backend.registration.entity.RegistrationStatus;
import java.time.LocalDateTime;

public record RegistrationDecisionResponse(
    Long id,
    RegistrationStatus status,
    String createdUserLogin,
    LocalDateTime reviewedAt,
    String message
) {
}
