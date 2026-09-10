package com.ediary.backend.user.controller;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.dto.request.ForgotPasswordRequest;
import com.ediary.backend.user.dto.request.ResetPasswordRequest;
import com.ediary.backend.user.service.PasswordResetService;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {
    private final PasswordResetService service;
    private final Clock clock;
    private final Map<String, Window> attempts = new HashMap<>();
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.AbortPolicy());

    public PasswordResetController(PasswordResetService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/forgot")
    public Map<String, String> forgot(@Valid @RequestBody ForgotPasswordRequest input, HttpServletRequest request) {
        limit(request.getRemoteAddr(), 5, "forgot");
        try {
            executor.execute(() -> {
                try { service.request(input.email()); }
                catch (Exception exception) {
                    LoggerFactory.getLogger(getClass()).error("Password reset request processing failed ({})", exception.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException exception) {
            throw new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE, "RESET_BUSY", "Сервіс тимчасово зайнятий. Спробуйте пізніше.");
        }
        return Map.of("message", "Якщо обліковий запис із цією адресою існує, інструкції для відновлення пароля буде надіслано на пошту. Перевірте папку «Спам».");
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@Valid @RequestBody ResetPasswordRequest input, HttpServletRequest request) {
        limit(request.getRemoteAddr(), 20, "reset");
        service.reset(input.token(), input.newPassword());
        return Map.of("message", "Пароль змінено. Увійдіть із новим паролем.");
    }

    private synchronized void limit(String ip, int maximum, String operation) {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        String key = operation + ":" + ip;
        Window window = attempts.get(key);
        if (window == null) {
            if (attempts.size() >= 10000) throw throttled();
            window = new Window(now + TimeUnit.MINUTES.toMillis(15));
            attempts.put(key, window);
        }
        if (++window.count > maximum) throw throttled();
    }

    private ApplicationException throttled() {
        return new ApplicationException(HttpStatus.TOO_MANY_REQUESTS, "RESET_RATE_LIMIT", "Забагато спроб. Повторіть через 15 хвилин.");
    }

    @PreDestroy
    void close() { executor.shutdown(); }

    private static class Window {
        final long expiresAt;
        int count;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
