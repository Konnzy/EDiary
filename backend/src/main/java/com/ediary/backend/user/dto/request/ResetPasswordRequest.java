package com.ediary.backend.user.dto.request;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43}$") String token,
    @NotBlank @Size(min = 8, max = 72)
    @Pattern(regexp = "(?s)^(?=.*[\\p{L}])(?=.*[0-9]).+$", message = "Пароль має містити літери та цифри")
    String newPassword
) {}
