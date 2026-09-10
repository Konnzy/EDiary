package com.ediary.backend.registration.dto.response;

public record RequestedStudentResponse(
    Long id,
    String fullName,
    String login,
    String className
) {
}
