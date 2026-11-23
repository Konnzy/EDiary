package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.Subject;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByTitleAsc();

    @Query("select subject from Subject subject where lower(subject.title) in :titles")
    List<Subject> findAllByNormalizedTitles(@Param("titles") Set<String> titles);

    boolean existsByTitleIgnoreCase(String title);
    @Query("select subject.id from Subject subject where lower(subject.title) = lower(:title)")
    List<Long> findIdsByTitleIgnoreCase(@Param("title") String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subject from Subject subject where subject.id = :subjectId")
    Optional<Subject> findByIdForUpdate(@Param("subjectId") Long subjectId);

    @Query("select exists (select teacher.id from TeacherProfile teacher join teacher.subjects subject where subject.id = :subjectId)")
    boolean hasTeachers(@Param("subjectId") Long subjectId);

    @Query("select exists (select entry.id from ScheduleEntry entry where entry.subject.id = :subjectId)")
    boolean hasSchedule(@Param("subjectId") Long subjectId);

    @Query("select exists (select request.id from RegistrationRequest request where request.requestedSubject.id = :subjectId)")
    boolean hasRegistrationRequests(@Param("subjectId") Long subjectId);

    @Query("select exists (select entry.id from ScheduleEntry entry where entry.subject.id = :subjectId and entry.teacher.id = :teacherId)")
    boolean isTeacherAssignmentUsedInSchedule(
        @Param("subjectId") Long subjectId,
        @Param("teacherId") Long teacherId
    );
}
