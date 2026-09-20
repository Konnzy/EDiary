package com.ediary.backend.user.admin.dto;

public record UserDeletionEligibilityResponse(
    boolean deletable,
    String reason
) {
}
