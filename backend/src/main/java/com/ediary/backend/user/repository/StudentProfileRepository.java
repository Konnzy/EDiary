package com.ediary.backend.user.repository;

import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user", "schoolClass"})
    Optional<StudentProfile> findByUser_LoginIgnoreCase(String login);

    long countBySchoolClassId(Long classId);

    boolean existsByUser_IdAndSchoolClassIsNotNull(Long userId);

    long deleteByUser_Id(Long userId);

    boolean existsByParentLinkCodeHash(String parentLinkCodeHash);

    @EntityGraph(attributePaths = {"user", "schoolClass"})
    Optional<StudentProfile> findByParentLinkCodeHashAndUser_StatusAndStatus(
        String parentLinkCodeHash,
        UserStatus userStatus,
        StudentStatus studentStatus
    );

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findAllBySchoolClassIdOrderBySurnameAscNameAsc(Long classId);

    @EntityGraph(attributePaths = {"user", "schoolClass"})
    List<StudentProfile> findAllBySchoolClassIdInOrderBySurnameAscNameAsc(List<Long> classIds);

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findAllBySchoolClassIsNullAndUser_StatusAndStatusOrderBySurnameAscNameAsc(
        UserStatus userStatus,
        StudentStatus studentStatus
    );

    @EntityGraph(attributePaths = {"user", "schoolClass", "schoolClass.academicYear"})
    @Query("""
        select student from StudentProfile student
        left join student.schoolClass schoolClass
        left join schoolClass.academicYear classYear
        where student.user.status = :userStatus
          and student.status = :studentStatus
          and (schoolClass is null or classYear.value <> :academicYear)
        order by student.surname, student.name
        """)
    List<StudentProfile> findAssignableForAcademicYear(
        @Param("academicYear") String academicYear,
        @Param("userStatus") UserStatus userStatus,
        @Param("studentStatus") StudentStatus studentStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "schoolClass"})
    @Query("select student from StudentProfile student where student.id = :studentId")
    Optional<StudentProfile> findByIdForUpdate(@Param("studentId") Long studentId);

    interface ClassStudentCount {
        Long getClassId();

        Long getStudentCount();
    }

    @Query("""
        select student.schoolClass.id as classId, count(student.id) as studentCount
        from StudentProfile student
        group by student.schoolClass.id
        """)
    List<ClassStudentCount> countStudentsByClass();
}
