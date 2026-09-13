package com.ediary.backend.user.controller;

import com.ediary.backend.user.dto.request.AdminLoginRequest;
import com.ediary.backend.user.dto.request.LoginRequest;
import com.ediary.backend.user.dto.response.AdminLoginResponse;
import com.ediary.backend.user.dto.response.CurrentSessionResponse;
import com.ediary.backend.user.dto.response.LoginResponse;
import com.ediary.backend.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        saveAuthentication(result.authentication(), httpRequest, httpResponse);
        return result.response();
    }

    @PostMapping("/admin/login")
    public AdminLoginResponse adminLogin(
        @Valid @RequestBody AdminLoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        AuthService.AdminAuthentication result = authService.authenticateAdministrator(request);
        saveAuthentication(result.authentication(), httpRequest, httpResponse);
        return result.response();
    }

    @GetMapping("/session")
    public CurrentSessionResponse currentSession(Authentication authentication) {
        return authService.currentSession(authentication.getName());
    }

    private void saveAuthentication(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
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
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
    }
}
