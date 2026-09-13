package com.ediary.backend.school.repository;

import com.ediary.backend.school.entity.ScheduleEntry;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<ScheduleEntry, Long> {

    @EntityGraph(attributePaths = {"subject", "teacher"})
    List<ScheduleEntry> findAllBySchoolClass_IdAndAcademicYearAndWeekNumberOrderByWeekdayAscLessonNumberAsc(
        Long classId,
        String academicYear,
        Integer weekNumber
    );

    @EntityGraph(attributePaths = {"schoolClass", "teacher"})
    @Query("""
        select entry
        from ScheduleEntry entry
        where entry.academicYear = :academicYear
            and entry.weekNumber = :weekNumber
            and entry.schoolClass.id <> :classId
        """)
    List<ScheduleEntry> findTeacherConflicts(
        @Param("classId") Long classId,
        @Param("academicYear") String academicYear,
        @Param("weekNumber") Integer weekNumber
    );

    @EntityGraph(attributePaths = {"subject", "teacher", "schoolClass"})
    @Query("""
        select entry
        from ScheduleEntry entry
        where lower(entry.teacher.user.login) = lower(:login)
            and entry.academicYear = :academicYear
            and entry.weekNumber = :weekNumber
        order by entry.weekday, entry.lessonNumber
        """)
    List<ScheduleEntry> findAllForTeacher(
        @Param("login") String login,
        @Param("academicYear") String academicYear,
        @Param("weekNumber") Integer weekNumber
    );

    @EntityGraph(attributePaths = {"subject", "teacher", "schoolClass"})
    List<ScheduleEntry> findAllByAcademicYearAndWeekNumberAndWeekdayOrderByLessonNumberAscSchoolClass_NumberAscSchoolClass_LetterAsc(
        String academicYear,
        Integer weekNumber,
        com.ediary.backend.school.entity.Weekday weekday
    );
}
