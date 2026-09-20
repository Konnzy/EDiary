package com.ediary.backend.user.admin.dto;

public record AdminUserCreationResponse(
    AdminUserResponse user,
    String message
) {
}
