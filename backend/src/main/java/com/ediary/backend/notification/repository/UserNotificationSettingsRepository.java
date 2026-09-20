package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.entity.UserNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettings, Long> {
    long deleteByUser_Id(Long userId);
}
