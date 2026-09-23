package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.ScheduleEntry;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<ScheduleEntry, Long> {

    interface TeacherClassAssignment {
        Long getTeacherId();
        Integer getClassNumber();
        String getClassLetter();
    }

    @Query("""
        select distinct entry.teacher.id as teacherId,
            entry.schoolClass.number as classNumber,
            entry.schoolClass.letter as classLetter
        from ScheduleEntry entry
        where entry.active = true
        order by entry.teacher.id, entry.schoolClass.number, entry.schoolClass.letter
        """)
    List<TeacherClassAssignment> findActiveTeacherClassAssignments();

    @Query("""
        select distinct entry.teacher.id as teacherId,
            entry.schoolClass.number as classNumber,
            entry.schoolClass.letter as classLetter
        from ScheduleEntry entry
        where entry.teacher.id = :teacherId
            and entry.academicYear = :academicYear
            and entry.active = true
        order by entry.teacher.id, entry.schoolClass.number, entry.schoolClass.letter
        """)
    List<TeacherClassAssignment> findActiveTeacherClassAssignmentsForYear(
        @Param("teacherId") Long teacherId,
        @Param("academicYear") String academicYear
    );

    boolean existsByTeacher_User_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"subject", "teacher.user", "schoolClass"})
    @Query("select entry from ScheduleEntry entry where entry.id = :scheduleId")
    Optional<ScheduleEntry> findByIdForLessonCreation(@Param("scheduleId") Long scheduleId);

    @EntityGraph(attributePaths = {"subject", "teacher"})
    List<ScheduleEntry> findAllBySchoolClass_IdAndAcademicYearAndWeekNumberAndActiveTrueOrderByWeekdayAscLessonNumberAsc(
        Long classId,
        String academicYear,
        Integer weekNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select entry from ScheduleEntry entry
        where entry.schoolClass.id = :classId
            and entry.academicYear = :academicYear
            and entry.weekNumber = :weekNumber
            and entry.active = true
        order by entry.weekday, entry.lessonNumber
        """)
    List<ScheduleEntry> findActiveForUpdate(
        @Param("classId") Long classId,
        @Param("academicYear") String academicYear,
        @Param("weekNumber") Integer weekNumber
    );

    @Query(value = "SELECT DISTINCT schedule_id FROM lesson WHERE schedule_id IN (:scheduleIds)", nativeQuery = true)
    List<Long> findIdsReferencedByLessons(@Param("scheduleIds") Collection<Long> scheduleIds);

    @EntityGraph(attributePaths = {"schoolClass", "teacher"})
    @Query("""
        select entry
        from ScheduleEntry entry
        where entry.academicYear = :academicYear
            and entry.weekNumber = :weekNumber
            and entry.schoolClass.id <> :classId
            and entry.active = true
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
            and entry.active = true
        order by entry.weekday, entry.lessonNumber
        """)
    List<ScheduleEntry> findAllForTeacher(
        @Param("login") String login,
        @Param("academicYear") String academicYear,
        @Param("weekNumber") Integer weekNumber
    );

    @EntityGraph(attributePaths = {"subject", "teacher", "schoolClass"})
    List<ScheduleEntry> findAllByAcademicYearAndWeekNumberAndWeekdayAndActiveTrueOrderByLessonNumberAscSchoolClass_NumberAscSchoolClass_LetterAsc(
        String academicYear,
        Integer weekNumber,
        com.ediary.backend.schedule.entity.Weekday weekday
    );
}
