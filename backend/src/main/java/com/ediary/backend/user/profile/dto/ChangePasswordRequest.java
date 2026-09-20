package com.ediary.backend.user.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank
    String currentPassword,

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "(?s)^(?=.*[\\p{L}])(?=.*[0-9]).+$", message = "Пароль має містити літери та цифри")
    String newPassword
) {
}
