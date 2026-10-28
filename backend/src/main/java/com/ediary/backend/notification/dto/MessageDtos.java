package com.ediary.backend.notification.dto;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.user.entity.UserRole;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public final class MessageDtos {
    private MessageDtos() {}
    public record Recipient(Long id, String fullName, UserRole role, boolean homeroom) {
        public Recipient(Long id, String fullName, UserRole role) { this(id, fullName, role, false); }
    }
    public record Send(@NotNull @Positive Long recipientId, @NotBlank @Size(max=150) String subject,
                       @NotBlank @Size(max=1000) String text, @NotNull UUID clientRequestId) {}
    public record Importance(@NotNull Boolean important) {}
    public record Item(Long id, Recipient sender, Recipient receiver, String subject, String text,
                       LocalDateTime createdAt, boolean read, boolean important, boolean incoming) {}
    public record Mailbox(PageResponse<Item> messages, long unreadCount) {}
}
