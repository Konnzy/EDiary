package com.ediary.backend.user.teacher.dto;

import java.util.List;

public record StudentTeachersResponse(String className, Teacher homeroomTeacher, List<Teacher> teachers) {
    public record Teacher(Long id, Long userId, String fullName, String email, String phone, List<Subject> subjects, boolean hasAvatar) {}
    public record Subject(Long id, String name) {}
}
