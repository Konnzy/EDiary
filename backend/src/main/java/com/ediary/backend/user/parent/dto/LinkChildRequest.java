package com.ediary.backend.user.parent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LinkChildRequest(
    @NotBlank(message = "Введіть код прив’язки до дитини")
    @Pattern(
        regexp = "^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$",
        message = "Код прив’язки має формат XXXX-XXXX"
    )
    String code
) {
}
