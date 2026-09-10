package com.ediary.backend.school.dto.response;

public record AdminSchoolClassResponse(
    Long id,
    Integer number,
    String letter,
    Integer limit,
    Long classTeacherId,
    String classTeacherName,
    long studentCount
) {
}
