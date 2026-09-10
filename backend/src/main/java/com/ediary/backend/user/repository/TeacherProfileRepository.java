package com.ediary.backend.user.repository;

import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {

    @EntityGraph(attributePaths = "user")
    List<TeacherProfile> findAllByUser_StatusOrderBySurnameAscNameAsc(UserStatus status);

    @EntityGraph(attributePaths = {"user", "subjects"})
    List<TeacherProfile> findAllByOrderBySurnameAscNameAsc();

    @EntityGraph(attributePaths = {"user", "subjects"})
    @Query("""
        select distinct teacher
        from TeacherProfile teacher
        join teacher.subjects subject
        where subject.id = :subjectId
        order by teacher.surname, teacher.name
        """)
    List<TeacherProfile> findAllAssignedToSubject(@Param("subjectId") Long subjectId);

    @EntityGraph(attributePaths = {"user", "subjects"})
    @Query("""
        select distinct teacher
        from TeacherProfile teacher
        where teacher.id in :teacherIds
        """)
    List<TeacherProfile> findAllForSchedule(@Param("teacherIds") Set<Long> teacherIds);
}
