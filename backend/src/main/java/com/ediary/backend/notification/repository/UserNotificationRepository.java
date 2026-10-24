package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.entity.UserNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findTop20ByRecipient_IdAndDismissedAtIsNullAndWithdrawnFalseAndVisibleInAppTrueOrderByCreatedAtDescIdDesc(Long recipientId);
    long countByRecipient_IdAndReadAtIsNullAndDismissedAtIsNullAndWithdrawnFalseAndVisibleInAppTrue(Long recipientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserNotification> findByIdAndRecipient_Id(Long id, Long recipientId);
    boolean existsByRecipient_IdAndEventKey(Long recipientId, String eventKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserNotification> findByRecipient_IdAndEventKey(Long recipientId, String eventKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from UserNotification notification where notification.id = :id")
    Optional<UserNotification> findForDelivery(@Param("id") Long id);

    @Query("select notification.id from UserNotification notification where notification.emailDueAt <= :now order by notification.emailDueAt, notification.id")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("update UserNotification notification set notification.readAt = CURRENT_TIMESTAMP where notification.recipient.id = :recipientId and notification.readAt is null and notification.dismissedAt is null and notification.withdrawn = false and notification.visibleInApp = true")
    int markAllRead(@Param("recipientId") Long recipientId);
}
