package com.ediary.backend.schedule.dto.response;

import java.util.List;

public record SubjectDeletionEligibilityResponse(
    boolean deletable,
    List<String> blockers
) {
}
