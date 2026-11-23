package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.SchoolClass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    boolean existsByClassTeacher_User_Id(Long userId);

    @EntityGraph(attributePaths = "classTeacher")
    List<SchoolClass> findAllByOrderByNumberAscLetterAsc();

    @EntityGraph(attributePaths = {"classTeacher", "academicYear"})
    List<SchoolClass> findAllByAcademicYearValueOrderByNumberAscLetterAsc(String academicYear);

    boolean existsByAcademicYearValueAndNumberAndLetterIgnoreCase(String academicYear, Integer number, String letter);
    @Query("""
        select schoolClass.id from SchoolClass schoolClass
        where schoolClass.academicYear.value = :year and schoolClass.number = :number
          and upper(schoolClass.letter) = upper(:letter)
        """)
    List<Long> findIdsByYearAndNumberAndLetter(
        @Param("year") String year, @Param("number") Integer number, @Param("letter") String letter);

    boolean existsByAcademicYearValueAndNumberAndLetterIgnoreCaseAndIdNot(
        String academicYear,
        Integer number,
        String letter,
        Long id
    );

    boolean existsByAcademicYearValueAndClassTeacherIdAndIdNot(String academicYear, Long teacherId, Long id);

    Optional<SchoolClass> findByClassTeacherId(Long teacherId);

    List<SchoolClass> findAllByClassTeacherIdOrderByNumberAscLetterAsc(Long teacherId);

    @Query("select exists (select entry.id from ScheduleEntry entry where entry.schoolClass.id = :classId)")
    boolean hasSchedule(@Param("classId") Long classId);

    @Query("select exists (select request.id from RegistrationRequest request where request.requestedClass.id = :classId)")
    boolean hasRegistrationRequests(@Param("classId") Long classId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"classTeacher", "academicYear"})
    @Query("select schoolClass from SchoolClass schoolClass where schoolClass.id = :classId")
    Optional<SchoolClass> findByIdForUpdate(@Param("classId") Long classId);
}
