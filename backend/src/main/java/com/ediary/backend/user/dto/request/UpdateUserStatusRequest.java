package com.ediary.backend.user.dto.request;

import com.ediary.backend.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull UserStatus status
) {
}
