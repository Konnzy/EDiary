package com.ediary.backend.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notifications.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationSchedulingConfig { }
