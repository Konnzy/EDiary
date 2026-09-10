package com.ediary.backend.user.dto.request;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(
    @NotBlank @Email @Size(max = 100) String email
) {}
