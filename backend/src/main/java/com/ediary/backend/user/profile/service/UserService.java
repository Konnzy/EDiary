package com.ediary.backend.user.profile.service;

import com.ediary.backend.user.profile.dto.ChangePasswordRequest;
import com.ediary.backend.user.profile.dto.UserProfileResponse;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse changeProfilePassword(Long userId, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Користувача не знайдено"));

        validateUserStatus(user);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Неправильний поточний пароль");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

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
            throw new IllegalStateException("Обліковий запис деактивовано");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new IllegalStateException("Обліковий запис заблоковано");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Обліковий запис неактивний");
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
