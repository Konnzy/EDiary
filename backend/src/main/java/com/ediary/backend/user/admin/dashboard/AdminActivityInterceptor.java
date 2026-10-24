package com.ediary.backend.user.admin.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminActivityInterceptor implements HandlerInterceptor {

    private static final String ACTOR_ATTRIBUTE = AdminActivityInterceptor.class.getName() + ".actor";

    private final AdminActivityRecorder recorder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            request.setAttribute(ACTOR_ATTRIBUTE, authentication.getName());
        }
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex
    ) {
        Object actor = request.getAttribute(ACTOR_ATTRIBUTE);
        if (!(actor instanceof String login) || ex != null || response.getStatus() < 200
            || response.getStatus() >= 300) return;

        AdminActivityAction action = AdminActivityAction.from(request);
        if (action == null) return;
        try {
            recorder.record(login, action);
        } catch (RuntimeException error) {
            log.error("event=admin_activity.failed", com.ediary.backend.common.logging.SafeExceptions.forLogging(error));
        }
    }
}
