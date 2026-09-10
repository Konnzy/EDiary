package com.ediary.backend.registration.dto.response;

import com.ediary.backend.registration.entity.RegistrationStatus;
import java.time.LocalDateTime;

public record RegistrationSubmissionResponse(
    Long id,
    RegistrationStatus status,
    String message,
    LocalDateTime submittedAt
) {
}
