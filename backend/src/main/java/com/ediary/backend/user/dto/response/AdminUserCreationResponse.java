package com.ediary.backend.user.dto.response;

public record AdminUserCreationResponse(
    AdminUserResponse user,
    String message
) {
}
