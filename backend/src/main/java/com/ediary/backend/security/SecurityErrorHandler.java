package com.ediary.backend.security;

import com.ediary.backend.common.logging.RequestDiagnostics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error) throws IOException {
        write(request, response, 401, "AUTHENTICATION_REQUIRED", "Потрібна авторизація");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error) throws IOException {
        if (error instanceof PasswordChangeRequiredException) {
            write(request, response, 403, "PASSWORD_CHANGE_REQUIRED", error.getMessage());
            return;
        }
        write(request, response, 403, error instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED", "Недостатньо прав для виконання цієї дії");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
        RequestDiagnostics.failure(request, status, code, null);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // All fields are fixed server strings or the validated request ID.
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
            + "\",\"requestId\":\"" + RequestDiagnostics.id(request) + "\"}");
    }
}
