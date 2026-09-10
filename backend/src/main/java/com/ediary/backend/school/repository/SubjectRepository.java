package com.ediary.backend.school.repository;

import com.ediary.backend.school.entity.Subject;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByTitleAsc();

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subject from Subject subject where subject.id = :subjectId")
    Optional<Subject> findByIdForUpdate(@Param("subjectId") Long subjectId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM teacher_subject WHERE subject_id = :subjectId)", nativeQuery = true)
    boolean hasTeachers(@Param("subjectId") Long subjectId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM schedule WHERE subject_id = :subjectId)", nativeQuery = true)
    boolean hasSchedule(@Param("subjectId") Long subjectId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM registration_request WHERE requested_subject_id = :subjectId)", nativeQuery = true)
    boolean hasRegistrationRequests(@Param("subjectId") Long subjectId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM schedule WHERE subject_id = :subjectId AND teacher_id = :teacherId)", nativeQuery = true)
    boolean isTeacherAssignmentUsedInSchedule(
        @Param("subjectId") Long subjectId,
        @Param("teacherId") Long teacherId
    );
}
