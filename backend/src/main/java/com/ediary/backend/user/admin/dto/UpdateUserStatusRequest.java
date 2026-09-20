package com.ediary.backend.user.admin.dto;

import com.ediary.backend.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull UserStatus status
) {
}
