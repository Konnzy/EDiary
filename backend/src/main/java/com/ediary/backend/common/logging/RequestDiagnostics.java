package com.ediary.backend.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
public final class RequestDiagnostics {
    public static final String REQUEST_ID = RequestDiagnostics.class.getName() + ".requestId";
    private static final String FAILURE_LOGGED = RequestDiagnostics.class.getName() + ".failureLogged";
    private RequestDiagnostics() {}

    public static String id(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID);
        return value instanceof String id ? id : "unavailable";
    }

    public static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value) return value;
        if (!request.getRequestURI().startsWith("/api/")) return "unmapped";
        var allowed = java.util.Set.of("api", "auth", "admin", "login", "logout", "session", "users", "me", "notifications",
            "registration-requests", "password", "forgot", "reset", "security", "csrf", "teacher", "student", "parent",
            "children", "classes", "subjects", "schedules", "lessons", "journal", "options", "academic-years", "read-all",
            "read", "attendance", "status", "reset-password", "students", "teachers", "avatar");
        return java.util.Arrays.stream(request.getRequestURI().split("/")).filter(value -> !value.isEmpty()).limit(12)
            .map(value -> allowed.contains(value) ? value : value.matches("[0-9]+") ? "{id}" : "{value}")
            .collect(java.util.stream.Collectors.joining("/", "/", ""));
    }

    public static void failure(HttpServletRequest request, int status, String code, Throwable error) {
        if (Boolean.TRUE.equals(request.getAttribute(FAILURE_LOGGED))) return;
        request.setAttribute(FAILURE_LOGGED, true);
        if (status >= 500) {
            log.error("event=request.failed method={} route={} status={} code={}", request.getMethod(), route(request), status, code,
                error == null ? null : SafeExceptions.forLogging(error));
        } else if (status == 401 || status == 403 || status == 429 || java.util.Set.of("INVALID_RESET_TOKEN", "INVALID_CURRENT_PASSWORD", "INVALID_PARENT_LINK_CODE").contains(code)) {
            log.warn("event=security.rejected method={} route={} status={} code={} accountId={}", request.getMethod(), route(request), status, code, org.slf4j.MDC.get("accountId"));
        } else {
            log.debug("event=request.rejected method={} route={} status={} code={}", request.getMethod(), route(request), status, code);
        }
    }
}
