package com.ediary.backend.user.profile.repository;

import com.ediary.backend.user.profile.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
    @Query("select avatar.updatedAt from UserAvatar avatar where avatar.userId = :userId")
    java.util.Optional<java.time.LocalDateTime> findUpdatedAtByUserId(@Param("userId") Long userId);
}
