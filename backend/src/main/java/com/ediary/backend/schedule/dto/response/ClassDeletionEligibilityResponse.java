package com.ediary.backend.schedule.dto.response;

import java.util.List;

public record ClassDeletionEligibilityResponse(
    boolean deletable,
    List<String> blockers
) {
}
