package com.ediary.backend.user.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePhoneRequest(
    @NotBlank
    @Pattern(regexp = "^(?=(?:\\D*\\d){10,15}\\D*$)\\+?[0-9 ()-]{10,20}$", message = "Введіть коректний номер телефону")
    String phone
) { }
