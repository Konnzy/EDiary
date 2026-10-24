package com.ediary.backend.user.auth.controller;

import com.ediary.backend.user.auth.dto.AdminLoginRequest;
import com.ediary.backend.user.auth.dto.LoginRequest;
import com.ediary.backend.user.auth.dto.AdminLoginResponse;
import com.ediary.backend.user.auth.dto.CurrentSessionResponse;
import com.ediary.backend.user.auth.dto.LoginResponse;
import com.ediary.backend.user.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final org.springframework.security.core.session.SessionRegistry sessionRegistry;

    @PostMapping("/login")
    public LoginResponse login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        AuthService.UserAuthentication result = authService.authenticateUser(request);
        saveAuthentication(result.authentication(), result.response().userId(), httpRequest, httpResponse);
        log.info("event=auth.login_succeeded actorId={} role={}", result.response().userId(), result.response().role());
        return result.response();
    }

    @PostMapping("/admin/login")
    public AdminLoginResponse adminLogin(
        @Valid @RequestBody AdminLoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        AuthService.AdminAuthentication result = authService.authenticateAdministrator(request);
        saveAuthentication(result.authentication(), result.response().userId(), httpRequest, httpResponse);
        log.info("event=auth.admin_login_succeeded actorId={} role={}", result.response().userId(), result.response().role());
        return result.response();
    }

    @GetMapping("/session")
    public CurrentSessionResponse currentSession(Authentication authentication) {
        return authService.currentSession(authentication.getName());
    }

    private void saveAuthentication(
        Authentication authentication,
        Long userId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("logging.actorId", userId);
        org.slf4j.MDC.put("actorId", String.valueOf(userId));
        session.setMaxInactiveInterval(30 * 60);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        sessionRegistry.registerNewSession(session.getId(), authentication.getPrincipal());
    }

    @PostMapping("/admin/logout")
    public void adminLogout(HttpServletRequest request, Authentication authentication) {
        logout(request, authentication);
    }

    @PostMapping("/logout")
    public void userLogout(HttpServletRequest request, Authentication authentication) {
        logout(request, authentication);
    }

    private void logout(HttpServletRequest request, Authentication authentication) {
        if (authentication != null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object actorId = session.getAttribute("logging.actorId");
                session.invalidate();
                log.info("event=auth.logout actorId={}", actorId instanceof Long ? actorId : null);
            }
            SecurityContextHolder.clearContext();
        }
    }
}
