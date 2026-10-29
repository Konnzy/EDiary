package com.ediary.backend.journal.dto;

import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.entity.HomeworkStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SaveJournalCellRequest(
    @Min(1) @Max(12) Integer score,
    AttendanceStatus attendanceStatus,
    HomeworkStatus homeworkStatus,
    @Size(max = 200) String comment,
    com.ediary.backend.journal.entity.GradeResult result
) {
    public SaveJournalCellRequest(Integer score,AttendanceStatus attendanceStatus,HomeworkStatus homeworkStatus,String comment) {
        this(score,attendanceStatus,homeworkStatus,comment,null);
    }
}
