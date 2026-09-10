package com.ediary.backend.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminLoginRequest(
    @NotBlank(message = "Введіть логін")
    String login,

    @NotBlank(message = "Введіть пароль")
    String password,

    @NotBlank(message = "Введіть службовий код")
    @Pattern(
        regexp = "(?i)^[A-Z0-9]{3}-?[A-Z0-9]{3}$",
        message = "Службовий код має формат XXX-XXX"
    )
    String accessCode
) {
}
