package com.ediary.backend.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserNotificationFeedResponse(long unreadCount, List<Item> items) {
    public record Item(Long id, String category, String title, String body, String target,
                       LocalDateTime createdAt, boolean read) { }
}
