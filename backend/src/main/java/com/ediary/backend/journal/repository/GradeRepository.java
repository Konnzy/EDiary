package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.Grade;
import com.ediary.backend.journal.entity.GradeResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    boolean existsByStudent_User_Id(Long userId);
    boolean existsByLesson_ScheduleEntry_Subject_Id(Long subjectId);
    boolean existsByLesson_ScheduleEntry_Subject_IdAndResult(Long subjectId, GradeResult result);

    List<Grade> findAllByLessonIdIn(List<Long> lessonIds);
    List<Grade> findAllByStudentIdAndLessonIdIn(Long studentId, List<Long> lessonIds);
    List<Grade> findAllByStudentIdAndLessonId(Long studentId, Long lessonId);
}
