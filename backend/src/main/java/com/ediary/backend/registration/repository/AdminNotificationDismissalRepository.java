package com.ediary.backend.registration.repository;

import com.ediary.backend.registration.entity.AdminNotificationDismissal;
import com.ediary.backend.registration.entity.RegistrationStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationDismissalRepository extends Repository<AdminNotificationDismissal, AdminNotificationDismissal.Key> {
    @Modifying
    @Query("""
        insert into AdminNotificationDismissal (id.adminId, id.requestId, id.requestStatus, dismissedAt)
        select :adminId, request.id, cast(request.status as String), :dismissedAt
        from RegistrationRequest request
        where request.status <> :pending
          and (:requestId is null or request.id = :requestId)
          and not exists (
              select 1 from AdminNotificationDismissal dismissal
              where dismissal.id.adminId = :adminId and dismissal.id.requestId = request.id
                and dismissal.id.requestStatus = cast(request.status as String)
          )
        """)
    void dismissReviewed(@Param("adminId") long adminId, @Param("requestId") Long requestId,
        @Param("pending") RegistrationStatus pending, @Param("dismissedAt") Instant dismissedAt);
}
