package com.ediary.backend.common.logging;

import java.sql.SQLException;

public final class SafeExceptions {
    private SafeExceptions() {}

    public static Throwable forLogging(Throwable source) {
        return copy(source, 0);
    }

    private static Throwable copy(Throwable source, int depth) {
        if (source == null || depth >= 6) return null;
        String description = source.getClass().getName();
        if (source instanceof SQLException sql && sql.getSQLState() != null
            && sql.getSQLState().matches("[A-Z0-9]{5}")) {
            description += " SQLState=" + sql.getSQLState();
        }
        // Exception messages can contain SQL values, credentials or reset links.
        var safe = new RuntimeException(description, copy(source.getCause(), depth + 1));
        safe.setStackTrace(source.getStackTrace());
        return safe;
    }
}
