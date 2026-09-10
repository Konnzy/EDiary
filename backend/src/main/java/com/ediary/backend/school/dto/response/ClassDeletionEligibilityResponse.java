package com.ediary.backend.school.dto.response;

import java.util.List;

public record ClassDeletionEligibilityResponse(
    boolean deletable,
    List<String> blockers
) {
}
