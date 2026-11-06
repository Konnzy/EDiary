package com.ediary.backend.user.profile.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.user.profile.dto.ChangePasswordRequest;
import com.ediary.backend.user.profile.dto.UserProfileResponse;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.password.repository.PasswordResetTokenRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuditService audit;
    private final PasswordResetTokenRepository resetTokens;

    @Transactional
    public UserProfileResponse changeProfilePassword(String login, ChangePasswordRequest request) {
        AppUser user = userRepository.findByLoginForUpdate(login)
                .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Користувача не знайдено"));

        validateUserStatus(user);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Неправильний поточний пароль");
        }

        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72
            || passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_NEW_PASSWORD", "Новий пароль має відрізнятися від поточного та містити не більше 72 байтів");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now(clock));

        userRepository.save(user);
        resetTokens.markAllUnusedAsUsed(user.getId());
        audit.record("PASSWORD_CHANGED", "USER", user.getId(), of(), of("passwordChanged", true));

        return toUserProfileResponse(user);
    }

    public UserProfileResponse findProfileById(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Користувача не знайдено"));

        validateUserStatus(user);

        return toUserProfileResponse(user);
    }

    public UserProfileResponse findProfileByLogin(String login) {
        AppUser user = userRepository
                .findByLoginIgnoreCase(login)
                .orElseThrow(() -> new IllegalArgumentException("Користувача не знайдено"));

        validateUserStatus(user);

        return toUserProfileResponse(user);
    }

    private void validateUserStatus(AppUser user) {
        if (UserStatus.DELETED.equals(user.getStatus())) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "Обліковий запис деактивовано");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "Обліковий запис заблоковано");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "Обліковий запис неактивний");
        }
    }

    private UserProfileResponse toUserProfileResponse(AppUser user) {
        return new UserProfileResponse(
                user.getId(),
                user.getLogin(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
