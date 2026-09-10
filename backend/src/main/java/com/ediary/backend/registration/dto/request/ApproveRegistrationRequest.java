package com.ediary.backend.registration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApproveRegistrationRequest(
    @NotBlank(message = "Введіть логін користувача")
    @Size(min = 3, max = 50, message = "Логін має містити від 3 до 50 символів")
    @Pattern(
        regexp = "^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,48}[A-Za-z0-9])$",
        message = "Логін має містити лише латинські літери, цифри, крапку, дефіс або підкреслення та починатися і закінчуватися літерою або цифрою"
    )
    String login
) {
}
