package com.ediary.backend.common.dashboard.dto;

import java.time.LocalDateTime;

public record AdminActivityResponse(
    Long id,
    String actorLogin,
    String title,
    String target,
    LocalDateTime occurredAt
) {
}
