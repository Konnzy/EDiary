package com.ediary.backend.common.error;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiErrorResponse(
    String code,
    String message,
    Map<String, String> fieldErrors,
    String path,
    LocalDateTime timestamp
) {
}
