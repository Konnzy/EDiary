package com.ediary.backend.school.dto.response;

import java.util.List;

public record SubjectDeletionEligibilityResponse(
    boolean deletable,
    List<String> blockers
) {
}
