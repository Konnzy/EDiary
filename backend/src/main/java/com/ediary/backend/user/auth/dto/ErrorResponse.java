package com.ediary.backend.user.auth.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
    String code,
    String message,
    String path,
    LocalDateTime timestamp
) {
}
