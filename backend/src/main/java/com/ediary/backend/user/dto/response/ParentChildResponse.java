package com.ediary.backend.user.dto.response;

import com.ediary.backend.user.entity.StudentStatus;

public record ParentChildResponse(
    Long studentId,
    String firstName,
    String lastName,
    String login,
    String email,
    String className,
    StudentStatus status,
    boolean primaryContact
) {
}
