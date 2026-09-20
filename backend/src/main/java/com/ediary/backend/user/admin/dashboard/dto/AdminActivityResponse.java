package com.ediary.backend.user.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminActivityResponse(
    Long id,
    String actorLogin,
    String title,
    String target,
    LocalDateTime occurredAt
) {
}
