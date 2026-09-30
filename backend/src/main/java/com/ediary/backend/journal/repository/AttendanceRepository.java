package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.Attendance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByStudent_User_Id(Long userId);
    List<Attendance> findAllByLessonIdIn(List<Long> lessonIds);
    List<Attendance> findAllByStudentIdAndLessonIdIn(Long studentId, List<Long> lessonIds);
    Optional<Attendance> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    @Query("select attendance from Attendance attendance where attendance.student.id = :studentId order by attendance.lesson.date desc, attendance.lesson.id desc")
    List<Attendance> findRecentByStudentId(@Param("studentId") Long studentId, Pageable pageable);
}
