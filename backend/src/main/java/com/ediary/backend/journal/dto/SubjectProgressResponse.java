package com.ediary.backend.journal.dto;

import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.entity.HomeworkStatus;
import com.ediary.backend.journal.entity.LessonWorkType;
import java.time.LocalDate;
import java.util.List;

public record SubjectProgressResponse(
    List<String> academicYears,
    String currentAcademicYear,
    List<Subject> subjects
) {
    public record Subject(
        Long id,
        String name,
        String teacher,
        List<Grade> grades,
        List<Attendance> attendance,
        List<Homework> homework
    ) { }
    public record Grade(Long id, LocalDate date, Integer lessonNumber, LessonWorkType workType,
                        Integer score, String comment) { }
    public record Attendance(Long id, LocalDate date, String topic, AttendanceStatus status) { }
    public record Homework(Long id, LocalDate assignedDate, LocalDate dueDate, String title, HomeworkStatus status) { }
}
