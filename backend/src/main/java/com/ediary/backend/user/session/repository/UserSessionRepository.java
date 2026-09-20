package com.ediary.backend.user.session.repository;

import com.ediary.backend.user.session.entity.UserSession;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    long deleteAllByUser_Id(Long userId);

    @Modifying
    @Query("""
        update UserSession session
           set session.revokedAt = :revokedAt,
               session.revokedReason = :reason
         where session.user.id = :userId
           and session.revokedAt is null
        """)
    int revokeActiveSessions(
        @Param("userId") Long userId,
        @Param("revokedAt") LocalDateTime revokedAt,
        @Param("reason") String reason
    );
}
