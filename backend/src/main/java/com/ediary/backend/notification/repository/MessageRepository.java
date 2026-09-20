package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    boolean existsBySender_IdOrReceiver_Id(Long senderId, Long receiverId);
}
