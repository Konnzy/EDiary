package com.ediary.backend.school.dto.response;

import com.ediary.backend.user.entity.UserStatus;

public record SubjectTeacherResponse(
    Long id,
    String fullName,
    UserStatus status
) {
}
