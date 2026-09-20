package com.ediary.backend.user.password.repository;

import com.ediary.backend.user.password.entity.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    long countByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime createdAfter);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Transactional
    void deleteByTokenHash(String tokenHash);

    long deleteAllByUser_Id(Long userId);

    @Modifying
    @Query("""
        update PasswordResetToken token
           set token.used = true
         where token.tokenHash = :tokenHash
           and token.used = false
           and token.expiresAt > :now
        """)
    int consume(
        @Param("tokenHash") String tokenHash,
        @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
        update PasswordResetToken token
           set token.used = true
         where token.user.id = :userId
           and token.used = false
        """)
    int markAllUnusedAsUsed(@Param("userId") Long userId);
}
