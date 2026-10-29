package com.ediary.backend.schedule.dto.response;

import java.util.List;

public record AdminSubjectResponse(
    Long id,
    String title,
    List<SubjectTeacherResponse> teachers,
    com.ediary.backend.journal.service.AssessmentPolicyService.Policy assessmentPolicy,
    boolean hasResults
) {
}
