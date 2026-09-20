package com.ediary.backend.user.password.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(
    @NotBlank @Email @Size(max = 100) String email
) {}
