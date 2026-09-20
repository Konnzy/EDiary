package com.ediary.backend.schedule.dto.response;

public record ClassTeacherOptionResponse(
    Long id,
    String fullName,
    Long assignedClassId,
    String assignedClassName
) {
}
