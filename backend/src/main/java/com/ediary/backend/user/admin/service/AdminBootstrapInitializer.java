package com.ediary.backend.user.admin.service;

import com.ediary.backend.user.entity.AdministratorProfile;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.AdministratorProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    private static final String LOGIN_PATTERN = "^[A-Za-z0-9._-]{4,50}$";
    private static final String ACCESS_CODE_PATTERN = "^[A-Z0-9]{6}$";

    private final UserRepository userRepository;
    private final AdministratorProfileRepository administratorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${app.bootstrap-admin.login:}")
    private String login;

    @Value("${app.bootstrap-admin.email:}")
    private String email;

    @Value("${app.bootstrap-admin.password:}")
    private String password;

    @Value("${app.bootstrap-admin.access-code:}")
    private String accessCode;

    @Value("${app.bootstrap-admin.first-name:}")
    private String firstName;

    @Value("${app.bootstrap-admin.last-name:}")
    private String lastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedLogin = login.strip();
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String normalizedCode = accessCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);

        validateConfiguration(normalizedLogin, normalizedEmail, normalizedCode);

        if (administratorProfileRepository.findByUserLoginIgnoreCase(normalizedLogin).isPresent()) {
            log.info("Bootstrap administrator already exists: {}", normalizedLogin);
            return;
        }
        if (userRepository.existsByLoginIgnoreCase(normalizedLogin)
            || userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("Bootstrap administrator login or email is already in use");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        AppUser user = new AppUser();
        user.setLogin(normalizedLogin);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.saveAndFlush(user);

        AdministratorProfile profile = new AdministratorProfile();
        profile.setUser(user);
        profile.setName(firstName.strip());
        profile.setSurname(lastName.strip());
        profile.setCodeHash(passwordEncoder.encode(normalizedCode));
        administratorProfileRepository.save(profile);

        log.info("Bootstrap administrator created: {}", normalizedLogin);
    }

    private void validateConfiguration(String normalizedLogin, String normalizedEmail, String normalizedCode) {
        if (!normalizedLogin.matches(LOGIN_PATTERN)) {
            throw new IllegalStateException("Bootstrap administrator login must contain 4-50 safe characters");
        }
        if (!normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || normalizedEmail.length() > 100) {
            throw new IllegalStateException("Bootstrap administrator email is invalid");
        }
        if (firstName.isBlank() || firstName.strip().length() > 50
            || lastName.isBlank() || lastName.strip().length() > 50) {
            throw new IllegalStateException("Bootstrap administrator name is invalid");
        }
        if (password.length() < 12
            || !password.matches(".*[A-Z].*")
            || !password.matches(".*[a-z].*")
            || !password.matches(".*[0-9].*")
            || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException("Bootstrap administrator password does not meet the security policy");
        }
        if (!normalizedCode.matches(ACCESS_CODE_PATTERN)) {
            throw new IllegalStateException("Bootstrap administrator access code must contain 6 letters or digits");
        }
    }
}
