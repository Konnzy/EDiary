package com.ediary.backend.journal.dto;

import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.entity.HomeworkStatus;
import com.ediary.backend.journal.entity.LessonWorkType;
import java.time.LocalDate;
import java.util.List;

public record JournalResponse(
    Long classId,
    String className,
    Long subjectId,
    String subjectName,
    String month,
    List<Student> students,
    List<Lesson> lessons
) {
    public record Student(Long id, String name, Double thematicEstimate) { }
    public record Lesson(
        Long id,
        Long scheduleId,
        LocalDate date,
        Integer lessonNumber,
        LessonWorkType workType,
        String topic,
        String homework,
        LocalDate homeworkDeadline,
        List<Cell> cells
    ) { }
    public record Cell(
        Long studentId,
        Integer score,
        AttendanceStatus attendanceStatus,
        HomeworkStatus homeworkStatus,
        String comment
    ) { }
}
