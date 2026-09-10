package com.ediary.backend.registration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRegistrationRequest(
    @NotBlank(message = "Вкажіть причину відхилення")
    @Size(min = 5, max = 500, message = "Причина має містити від 5 до 500 символів")
    String reason
) {
}
