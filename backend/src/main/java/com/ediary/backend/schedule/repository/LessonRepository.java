package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.Lesson;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @EntityGraph(attributePaths = {
        "scheduleEntry.subject",
        "scheduleEntry.teacher.user",
        "scheduleEntry.schoolClass"
    })
    Optional<Lesson> findByScheduleEntry_IdAndDate(Long scheduleId, LocalDate date);

    @EntityGraph(attributePaths = {
        "scheduleEntry.subject",
        "scheduleEntry.teacher.user",
        "scheduleEntry.schoolClass"
    })
    @Query("""
        select lesson
        from Lesson lesson
        where lesson.id = :lessonId
          and lower(lesson.scheduleEntry.teacher.user.login) = lower(:teacherLogin)
        """)
    Optional<Lesson> findForTeacher(
        @Param("lessonId") Long lessonId,
        @Param("teacherLogin") String teacherLogin
    );

    @EntityGraph(attributePaths = {
        "scheduleEntry.subject",
        "scheduleEntry.teacher.user",
        "scheduleEntry.schoolClass"
    })
    @Query("""
        select lesson
        from Lesson lesson
        where lower(lesson.scheduleEntry.teacher.user.login) = lower(:teacherLogin)
          and lesson.date = :date
        order by lesson.scheduleEntry.lessonNumber
        """)
    List<Lesson> findAllForTeacherByDate(
        @Param("teacherLogin") String teacherLogin,
        @Param("date") LocalDate date
    );
}
