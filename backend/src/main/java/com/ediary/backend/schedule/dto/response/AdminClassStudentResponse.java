package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.user.entity.StudentStatus;

public record AdminClassStudentResponse(
    Long id,
    String fullName,
    String login,
    String email,
    StudentStatus status
) {
}
