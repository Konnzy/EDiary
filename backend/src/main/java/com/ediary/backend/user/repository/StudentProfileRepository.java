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

    long countBySchoolClassId(Long classId);

    boolean existsByParentLinkCodeHash(String parentLinkCodeHash);

    @EntityGraph(attributePaths = {"user", "schoolClass"})
    Optional<StudentProfile> findByParentLinkCodeHashAndUser_StatusAndStatus(
        String parentLinkCodeHash,
        UserStatus userStatus,
        StudentStatus studentStatus
    );

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findAllBySchoolClassIdOrderBySurnameAscNameAsc(Long classId);

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findAllBySchoolClassIsNullAndUser_StatusAndStatusOrderBySurnameAscNameAsc(
        UserStatus userStatus,
        StudentStatus studentStatus
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
