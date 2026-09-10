package com.ediary.backend.user.dto.response;

public record UserDeletionEligibilityResponse(
    boolean deletable,
    String reason
) {
}
