package com.ediary.backend.common.logging;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class DiagnosticContext {
    private DiagnosticContext() {}

    public static Runnable capture(Runnable action) {
        var captured = MDC.getCopyOfContextMap();
        return () -> {
            var previous = MDC.getCopyOfContextMap();
            try {
                MDC.clear();
                if (captured != null) MDC.setContextMap(captured);
                action.run();
            } finally {
                MDC.clear();
                if (previous != null) MDC.setContextMap(previous);
            }
        };
    }

    public static void job(String name, Runnable action) {
        var previous = MDC.getCopyOfContextMap();
        try {
            MDC.clear();
            MDC.put("requestId", UUID.randomUUID().toString());
            MDC.put("job", name);
            action.run();
        } finally {
            MDC.clear();
            if (previous != null) MDC.setContextMap(previous);
        }
    }

    public static void afterCommit(Runnable action) {
        Runnable captured = capture(action);
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { captured.run(); }
            });
        } else captured.run();
    }
}
