package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.journal.entity.LessonWorkType;
import com.ediary.backend.schedule.entity.LessonStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @Query("""
        select count(lesson) > 0 from Lesson lesson
        where lesson.id = :lessonId and (
            exists (select grade.id from Grade grade where grade.lesson = lesson)
            or exists (select attendance.id from Attendance attendance where attendance.lesson = lesson)
            or exists (select homework.id from Homework homework where homework.lesson = lesson)
        )
        """)
    boolean hasJournalRecords(@Param("lessonId") Long lessonId);

    @Query("""
        select count(lesson) > 0 from Lesson lesson
        where lesson.scheduleEntry.schoolClass.id = :classId
          and lesson.scheduleEntry.subject.id = :subjectId
          and lesson.workType = :workType
          and lesson.date between :start and :end
          and lesson.id <> :excludedLessonId
          and lesson.status <> :excludedStatus
        """)
    boolean existsOtherJournalWorkType(
        @Param("classId") Long classId,
        @Param("subjectId") Long subjectId,
        @Param("workType") LessonWorkType workType,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end,
        @Param("excludedLessonId") Long excludedLessonId,
        @Param("excludedStatus") LessonStatus excludedStatus
    );

    @EntityGraph(attributePaths = {"scheduleEntry.subject", "scheduleEntry.teacher.user", "scheduleEntry.schoolClass"})
    @Query("""
        select lesson from Lesson lesson
        where lesson.scheduleEntry.schoolClass.id = :classId
          and lesson.scheduleEntry.subject.id = :subjectId
          and lesson.date between :start and :end
        order by lesson.date, lesson.scheduleEntry.lessonNumber
        """)
    List<Lesson> findJournalLessons(
        @Param("classId") Long classId,
        @Param("subjectId") Long subjectId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @EntityGraph(attributePaths = {"scheduleEntry.subject", "scheduleEntry.teacher.user", "scheduleEntry.schoolClass"})
    @Query("""
        select lesson from Lesson lesson
        where lesson.scheduleEntry.schoolClass.id = :classId
          and lesson.date between :start and :end
        order by lesson.date, lesson.scheduleEntry.lessonNumber
        """)
    List<Lesson> findClassLessons(
        @Param("classId") Long classId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

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
