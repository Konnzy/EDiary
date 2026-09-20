package com.ediary.backend.schedule.dto.response;

import java.util.List;

public record AdminSubjectOptionsResponse(
    List<SubjectTeacherResponse> teachers
) {
}
