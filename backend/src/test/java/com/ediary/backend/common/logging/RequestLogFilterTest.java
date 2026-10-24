package com.ediary.backend.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.ediary.backend.common.error.GlobalExceptionHandler;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class RequestLogFilterTest {
    @Test
    void correlatesValidClientIdAndRestoresMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        var response = new MockHttpServletResponse();
        String id = UUID.randomUUID().toString();
        request.addHeader("X-Request-ID", id);
        MDC.put("requestId", "outer");
        try {
            new RequestLogFilter().doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(id));
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
            assertThat(MDC.get("requestId")).isEqualTo("outer");
        } finally { MDC.clear(); }
    }

    @Test
    void logsUnexpectedFailureOnceWithSafeCauseAndNoQueryOrPayload() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestDiagnostics.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var request = new MockHttpServletRequest("POST", "/api/auth/password/reset");
        request.setQueryString("token=secret-token");
        request.addHeader("X-Request-ID", "invalid\npassword=secret");
        var response = new MockHttpServletResponse();
        try {
            new RequestLogFilter().doFilter(request, response, (req, res) -> {
                var result = new GlobalExceptionHandler().handleUnexpectedException(
                    new IllegalStateException("password=secret", new SQLException("token=secret-token", "42704")), request);
                assertThat(result.getBody().requestId()).isEqualTo(response.getHeader("X-Request-ID"));
                response.setStatus(result.getStatusCode().value());
            });
            assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");
            var failures = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(failures).hasSize(1);
            String text = failures.getFirst().getFormattedMessage() + ThrowableProxyUtil.asString(failures.getFirst().getThrowableProxy());
            assertThat(text).contains("SQLState=42704").doesNotContain("password=secret", "secret-token", "token=");
            assertThat(MDC.get("requestId")).isNull();
        } finally { logger.detachAppender(appender); MDC.clear(); }
    }

    @Test
    void asyncTaskRestoresWorkerContext() {
        MDC.put("requestId", "request");
        Runnable task = DiagnosticContext.capture(() -> assertThat(MDC.get("requestId")).isEqualTo("request"));
        MDC.put("requestId", "worker");
        try {
            task.run();
            assertThat(MDC.get("requestId")).isEqualTo("worker");
        } finally { MDC.clear(); }
    }
}
