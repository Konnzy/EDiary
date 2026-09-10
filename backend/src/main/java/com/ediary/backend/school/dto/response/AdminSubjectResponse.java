package com.ediary.backend.school.dto.response;

import java.util.List;

public record AdminSubjectResponse(
    Long id,
    String title,
    List<SubjectTeacherResponse> teachers
) {
}
