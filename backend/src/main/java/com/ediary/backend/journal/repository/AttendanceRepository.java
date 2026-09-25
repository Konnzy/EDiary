package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.Attendance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByStudent_User_Id(Long userId);

    List<Attendance> findAllByLessonIdIn(List<Long> lessonIds);
    List<Attendance> findAllByStudentIdAndLessonIdIn(Long studentId, List<Long> lessonIds);
    Optional<Attendance> findByStudentIdAndLessonId(Long studentId, Long lessonId);
}
