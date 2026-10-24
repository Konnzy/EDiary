package com.ediary.backend.common.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AuditServiceTest {
    @Test
    void logsOnlyAfterCommitAndDoesNotPrintSnapshots() {
        var repository = mock(AuditEntryRepository.class);
        var service = new AuditService(repository, mock(UserRepository.class), JsonMapper.builder().build(), Clock.systemUTC());
        var logger = (Logger) LoggerFactory.getLogger(AuditService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.record("LESSON_UPDATED", "LESSON", 1L, AuditValues.of("topic", "old-private-topic"), AuditValues.of("topic", "new-private-topic"));
            verify(repository).save(any());
            assertThat(appender.list).isEmpty();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("audit.committed").doesNotContain("private-topic");
        } finally { TransactionSynchronizationManager.clearSynchronization(); logger.detachAppender(appender); }
    }

    @Test
    void rollbackDoesNotEmitSuccessAndSecretFieldsAreRejected() {
        var repository = mock(AuditEntryRepository.class);
        var service = new AuditService(repository, mock(UserRepository.class), JsonMapper.builder().build(), Clock.systemUTC());
        var logger = (Logger) LoggerFactory.getLogger(AuditService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.record("USER_UPDATED", "USER", 1L, Map.of(), Map.of("password", "secret")))
                .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(repository);
            service.record("PASSWORD_CHANGED", "USER", 1L, Map.of(), AuditValues.of("passwordChanged", true));
            TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(appender.list).isEmpty();
        } finally { TransactionSynchronizationManager.clearSynchronization(); logger.detachAppender(appender); }
    }
}
