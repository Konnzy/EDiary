package com.ediary.backend.schedule.dto.response;

public record AdminStudentParentContactResponse(
    Long id,
    String fullName,
    String email,
    String phone
) {
}
