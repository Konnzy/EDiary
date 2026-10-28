package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Message> {
    boolean existsBySender_IdOrReceiver_Id(Long senderId, Long receiverId);
    long countByReceiver_IdAndReadFalse(Long userId);
    java.util.Optional<Message> findBySender_IdAndClientRequestId(Long senderId, java.util.UUID clientRequestId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.id=:id and (m.sender.id=:userId or m.receiver.id=:userId)")
    java.util.Optional<Message> findOwned(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("userId") Long userId);
}
