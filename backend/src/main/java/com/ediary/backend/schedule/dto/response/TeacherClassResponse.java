package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.user.entity.StudentStatus;
import java.util.List;

public record TeacherClassResponse(
    Long id,
    String name,
    String academicYear,
    String classTeacherName,
    int capacity,
    List<Student> students,
    Statistics statistics
) {
    public record Student(
        Long id,
        String fullName,
        String login,
        String email,
        StudentStatus status,
        List<ParentContact> parents
    ) {
    }

    public record ParentContact(Long id, String fullName, String email, String phone) {
    }

    public record Statistics(
        Double attendancePercent,
        long attended,
        long recordedAttendance,
        Double averageGrade,
        long gradeCount
    ) {
    }
}
