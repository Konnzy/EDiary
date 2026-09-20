package com.ediary.backend.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.AdministratorProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AdministratorProfileRepository administratorProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(administratorProfileRepository, userRepository, passwordEncoder);
    }

    @Test
    void returnsCurrentActiveSessionUser() {
        AppUser user = user(15L, "student.one", UserRole.STUDENT, UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        when(userRepository.findByLoginIgnoreCase("student.one")).thenReturn(Optional.of(user));

        var response = service.currentSession("student.one");

        assertThat(response.userId()).isEqualTo(15L);
        assertThat(response.login()).isEqualTo("student.one");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void rejectsSessionForBlockedUser() {
        AppUser user = user(16L, "teacher.blocked", UserRole.TEACHER, UserStatus.BLOCKED);
        when(userRepository.findByLoginIgnoreCase("teacher.blocked")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.currentSession("teacher.blocked"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("ACCOUNT_UNAVAILABLE"));
    }

    @Test
    void rejectsSessionForDeletedUser() {
        when(userRepository.findByLoginIgnoreCase("missing.user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentSession("missing.user"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("SESSION_INVALID"));
    }

    private AppUser user(Long id, String login, UserRole role, UserStatus status) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setLogin(login);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
