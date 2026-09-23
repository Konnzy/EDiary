package com.ediary.backend.schedule.dto.response;

public record AdminSchoolClassResponse(
    Long id,
    Integer number,
    String letter,
    Integer limit,
    Long classTeacherId,
    String classTeacherName,
    long studentCount,
    String academicYear,
    boolean editable,
    String status
) {
}
