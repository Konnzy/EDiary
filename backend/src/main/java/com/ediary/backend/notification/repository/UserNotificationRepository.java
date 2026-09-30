package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.entity.UserNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findTop20ByRecipient_IdOrderByCreatedAtDescIdDesc(Long recipientId);
    long countByRecipient_IdAndReadAtIsNull(Long recipientId);
    Optional<UserNotification> findByIdAndRecipient_Id(Long id, Long recipientId);
    boolean existsByRecipient_IdAndEventKey(Long recipientId, String eventKey);

    @Modifying
    @Query("update UserNotification notification set notification.readAt = CURRENT_TIMESTAMP where notification.recipient.id = :recipientId and notification.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId);
}
