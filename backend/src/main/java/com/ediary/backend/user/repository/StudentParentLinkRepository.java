package com.ediary.backend.user.repository;

import com.ediary.backend.user.entity.StudentParentId;
import com.ediary.backend.user.entity.StudentParentLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentParentLinkRepository extends JpaRepository<StudentParentLink, StudentParentId> {

    boolean existsByStudent_Id(Long studentId);

    boolean existsByStudent_User_Id(Long userId);

    boolean existsByParent_User_Id(Long userId);

    @EntityGraph(attributePaths = {"student.user", "student.schoolClass"})
    @Query("""
        select link from StudentParentLink link
        where lower(link.parent.user.login) = lower(:login)
        order by link.student.surname, link.student.name
        """)
    List<StudentParentLink> findAllByParentLogin(@Param("login") String login);

    @EntityGraph(attributePaths = {"student.user", "student.schoolClass"})
    @Query("""
        select link from StudentParentLink link
        where lower(link.parent.user.login) = lower(:login)
          and link.student.id = :studentId
        """)
    Optional<StudentParentLink> findByParentLoginAndStudentId(
        @Param("login") String login,
        @Param("studentId") Long studentId
    );
}
