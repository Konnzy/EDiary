package com.ediary.backend.user.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.user.entity.AdministratorProfile;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.AdministratorProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdministratorProfileRepository administratorProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdministratorWithHashedSecrets() {
        AdminBootstrapInitializer initializer = new AdminBootstrapInitializer(
            userRepository,
            administratorProfileRepository,
            passwordEncoder,
            Clock.fixed(Instant.parse("2026-05-24T10:00:00Z"), ZoneOffset.UTC)
        );
        setProperty(initializer, "login", "admin");
        setProperty(initializer, "email", "ADMIN@school.edu.ua");
        setProperty(initializer, "password", "StrongPassword!234");
        setProperty(initializer, "accessCode", "abc-123");
        setProperty(initializer, "firstName", "Олена");
        setProperty(initializer, "lastName", "Ткаченко");

        when(administratorProfileRepository.findByUserLoginIgnoreCase("admin"))
            .thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPassword!234")).thenReturn("password-hash");
        when(passwordEncoder.encode("ABC123")).thenReturn("code-hash");
        when(userRepository.saveAndFlush(any(AppUser.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(null);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        AppUser user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo("admin@school.edu.ua");
        assertThat(user.getPasswordHash()).isEqualTo("password-hash");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<AdministratorProfile> profileCaptor = ArgumentCaptor.forClass(AdministratorProfile.class);
        verify(administratorProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getCodeHash()).isEqualTo("code-hash");
        assertThat(profileCaptor.getValue().getUser()).isSameAs(user);
    }

    private void setProperty(AdminBootstrapInitializer initializer, String name, String value) {
        ReflectionTestUtils.setField(initializer, name, value);
    }
}
