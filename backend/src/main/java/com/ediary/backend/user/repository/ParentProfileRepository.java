package com.ediary.backend.user.repository;

import com.ediary.backend.user.entity.ParentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, Long> {
    Optional<ParentProfile> findByUser_Id(Long userId);

    long deleteByUser_Id(Long userId);

    @Query("select parent from ParentProfile parent where lower(parent.user.login) = lower(:login)")
    Optional<ParentProfile> findByUser_LoginIgnoreCase(@Param("login") String login);
}
