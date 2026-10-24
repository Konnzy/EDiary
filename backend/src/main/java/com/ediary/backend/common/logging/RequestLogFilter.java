package com.ediary.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLogFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var previous = MDC.getCopyOfContextMap();
        String incoming = request.getHeader("X-Request-ID");
        String id = incoming != null && incoming.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")
            ? incoming.toLowerCase(java.util.Locale.ROOT) : UUID.randomUUID().toString();
        request.setAttribute(RequestDiagnostics.REQUEST_ID, id);
        response.setHeader("X-Request-ID", id);
        MDC.put("requestId", id);
        var session = request.getSession(false);
        if (session != null && session.getAttribute("logging.actorId") instanceof Long actorId) MDC.put("actorId", actorId.toString());
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            failed = true;
            RequestDiagnostics.failure(request, 500, "UNHANDLED_EXCEPTION", error);
            throw error;
        } finally {
            int status = failed ? 500 : response.getStatus();
            long duration = (System.nanoTime() - started) / 1_000_000;
            if (status >= 400) RequestDiagnostics.failure(request, status, "HTTP_" + status, null);
            else if (!"GET".equals(request.getMethod()) && !"OPTIONS".equals(request.getMethod())) {
                log.info("event=request.completed method={} route={} status={} durationMs={}", request.getMethod(), RequestDiagnostics.route(request), status, duration);
            } else {
                log.debug("event=request.completed method={} route={} status={} durationMs={}", request.getMethod(), RequestDiagnostics.route(request), status, duration);
            }
            MDC.clear();
            if (previous != null) MDC.setContextMap(previous);
        }
    }
}
