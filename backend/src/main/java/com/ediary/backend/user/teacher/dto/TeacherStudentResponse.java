package com.ediary.backend.user.teacher.dto;

import java.util.List;

public record TeacherStudentResponse(
    Long id, Long userId, String fullName, Long classId, String className,
    List<Subject> subjects, List<ParentContact> parents
) {
    public record Subject(Long id, String name) {}
    public record ParentContact(String fullName, String phone, String email) {}
}
