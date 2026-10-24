package com.ediary.backend.notification.service;

import com.ediary.backend.notification.repository.UserNotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailDispatcher {
    private final UserNotificationRepository notifications;
    private final NotificationEmailSender sender;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.notifications.email-poll-interval:10000}", initialDelayString = "${app.notifications.email-poll-interval:10000}")
    public void dispatch() {
        com.ediary.backend.common.logging.DiagnosticContext.job("notification-email", this::runBatch);
    }

    private void runBatch() {
        long started = System.nanoTime();
        java.util.List<Long> ids;
        try {
            ids = notifications.findDueIds(LocalDateTime.now(clock), PageRequest.of(0, 50));
        } catch (RuntimeException error) {
            log.error("event=notification_job.failed", com.ediary.backend.common.logging.SafeExceptions.forLogging(error));
            return;
        }
        if (!ids.isEmpty()) log.info("event=notification_job.started count={}", ids.size());
        int failures = 0;
        for (Long id : ids) {
            try {
                sender.deliver(id);
            } catch (RuntimeException error) {
                failures++;
                log.error("event=notification_email.processing_failed notificationId={}", id, com.ediary.backend.common.logging.SafeExceptions.forLogging(error));
            }
        }
        if (!ids.isEmpty()) log.info("event=notification_job.completed count={} failures={} durationMs={}", ids.size(), failures, (System.nanoTime() - started) / 1_000_000);
    }
}
