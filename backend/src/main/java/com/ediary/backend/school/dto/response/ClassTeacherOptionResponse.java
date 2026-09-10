package com.ediary.backend.school.dto.response;

public record ClassTeacherOptionResponse(
    Long id,
    String fullName,
    Long assignedClassId,
    String assignedClassName
) {
}
