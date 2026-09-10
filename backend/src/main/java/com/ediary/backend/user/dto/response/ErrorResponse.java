package com.ediary.backend.user.dto.response;

import java.time.LocalDateTime;

public record ErrorResponse(
    String code,
    String message,
    String path,
    LocalDateTime timestamp
) {
}
