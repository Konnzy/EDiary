package com.ediary.backend.user.auth.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.auth.dto.AdminLoginRequest;
import com.ediary.backend.user.auth.dto.LoginRequest;
import com.ediary.backend.user.auth.dto.AdminLoginResponse;
import com.ediary.backend.user.auth.dto.CurrentSessionResponse;
import com.ediary.backend.user.auth.dto.LoginResponse;
import com.ediary.backend.user.entity.AdministratorProfile;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.AdministratorProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdministratorProfileRepository administratorProfileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAuthentication authenticateUser(LoginRequest request) {
        AppUser user = userRepository.findByLoginIgnoreCase(request.login().strip()).orElse(null);

        if (user == null) {
            consumePasswordHashingTime(request.password());
            throw invalidCredentials();
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!passwordMatches || user.getRole() == UserRole.ADMIN) {
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_UNAVAILABLE",
                "Обліковий запис недоступний"
            );
        }

        Authentication authentication = createAuthentication(user);
        LoginResponse response = new LoginResponse(
            user.getId(),
            user.getLogin(),
            user.getRole(),
            user.isMustChangePassword()
        );
        return new UserAuthentication(authentication, response);
    }

    public AdminAuthentication authenticateAdministrator(AdminLoginRequest request) {
        AdministratorProfile administrator = administratorProfileRepository
            .findByUserLoginIgnoreCase(request.login().strip())
            .orElse(null);

        if (administrator == null) {
            consumePasswordHashingTime(request.password());
            throw invalidCredentials();
        }

        AppUser user = administrator.getUser();
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        boolean codeMatches = passwordEncoder.matches(
            normalizeAccessCode(request.accessCode()),
            administrator.getCodeHash()
        );

        if (!passwordMatches || !codeMatches || user.getRole() != UserRole.ADMIN) {
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_UNAVAILABLE",
                "Обліковий запис недоступний"
            );
        }

        Authentication authentication = createAuthentication(user);
        AdminLoginResponse response = new AdminLoginResponse(
            user.getId(),
            user.getLogin(),
            user.getRole(),
            administrator.getName() + " " + administrator.getSurname()
        );
        return new AdminAuthentication(authentication, response);
    }

    public CurrentSessionResponse currentSession(String login) {
        AppUser user = userRepository.findByLoginIgnoreCase(login).orElseThrow(() ->
            new ApplicationException(
                HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID",
                "Сесію не знайдено. Увійдіть до системи ще раз"
            )
        );

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_UNAVAILABLE",
                "Обліковий запис недоступний"
            );
        }

        return new CurrentSessionResponse(
            user.getId(),
            user.getLogin(),
            user.getRole(),
            user.isMustChangePassword()
        );
    }

    private String normalizeAccessCode(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private void consumePasswordHashingTime(String password) {
        passwordEncoder.matches(password, passwordEncoder.encode("invalid-credentials-placeholder"));
    }

    private Authentication createAuthentication(AppUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(
            user.getLogin(),
            null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private ApplicationException invalidCredentials() {
        return new ApplicationException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "Невірні облікові дані"
        );
    }

    public record AdminAuthentication(
        Authentication authentication,
        AdminLoginResponse response
    ) {
    }

    public record UserAuthentication(
        Authentication authentication,
        LoginResponse response
    ) {
    }
}
