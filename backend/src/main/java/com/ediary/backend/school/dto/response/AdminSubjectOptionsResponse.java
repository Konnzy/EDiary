package com.ediary.backend.school.dto.response;

import java.util.List;

public record AdminSubjectOptionsResponse(
    List<SubjectTeacherResponse> teachers
) {
}
