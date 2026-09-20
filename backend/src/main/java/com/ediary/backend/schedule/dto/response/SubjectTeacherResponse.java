package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.user.entity.UserStatus;

public record SubjectTeacherResponse(
    Long id,
    String fullName,
    UserStatus status
) {
}
