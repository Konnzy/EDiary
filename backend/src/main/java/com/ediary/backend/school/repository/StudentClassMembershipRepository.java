package com.ediary.backend.school.repository;

import com.ediary.backend.school.entity.StudentClassMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentClassMembershipRepository extends JpaRepository<StudentClassMembership, Long> {

    long countBySchoolClassIdAndActiveTrue(Long classId);

    long countBySchoolClassId(Long classId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<StudentClassMembership> findAllBySchoolClassIdAndActiveTrueOrderByStudentSurnameAscStudentNameAsc(
        Long classId
    );

    Optional<StudentClassMembership> findByStudentIdAndSchoolClassIdAndActiveTrue(Long studentId, Long classId);

    Optional<StudentClassMembership> findByStudentIdAndSchoolClassId(Long studentId, Long classId);

    Optional<StudentClassMembership> findByStudentIdAndSchoolClassAcademicYearValueAndActiveTrue(
        Long studentId,
        String academicYear
    );
}
