package com.ediary.backend.journal.service;

import com.ediary.backend.journal.entity.Attendance;
import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.user.entity.StudentProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public Attendance save(StudentProfile student, Lesson lesson, AttendanceStatus status) {
        Attendance attendance = attendanceRepository
            .findByStudentIdAndLessonId(student.getId(), lesson.getId()).orElse(null);
        if (status == null) {
            if (attendance != null) attendanceRepository.delete(attendance);
            return null;
        }
        if (attendance == null) {
            attendance = new Attendance();
            attendance.setStudent(student);
            attendance.setLesson(lesson);
        }
        attendance.setStatus(status);
        return attendanceRepository.save(attendance);
    }
}
