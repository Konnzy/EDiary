package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.StudentClassMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentClassMembershipRepository extends JpaRepository<StudentClassMembership, Long> {

    boolean existsByStudent_User_Id(Long userId);

    long countBySchoolClassIdAndActiveTrue(Long classId);

    long deleteBySchoolClassIdAndActiveFalse(Long classId);

    boolean existsByStudentIdAndSchoolClassId(Long studentId, Long classId);

    @EntityGraph(attributePaths = {"schoolClass", "schoolClass.academicYear"})
    List<StudentClassMembership> findAllByStudentIdAndSchoolClassAcademicYearValue(
        Long studentId, String academicYear
    );

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<StudentClassMembership> findAllBySchoolClassIdAndActiveTrueOrderByStudentSurnameAscStudentNameAsc(
        Long classId
    );

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<StudentClassMembership> findAllBySchoolClassIdOrderByStudentSurnameAscStudentNameAsc(Long classId);

    Optional<StudentClassMembership> findByStudentIdAndSchoolClassIdAndActiveTrue(Long studentId, Long classId);

    Optional<StudentClassMembership> findByStudentIdAndSchoolClassId(Long studentId, Long classId);

    Optional<StudentClassMembership> findByStudentIdAndSchoolClassAcademicYearValueAndActiveTrue(
        Long studentId,
        String academicYear
    );
}
