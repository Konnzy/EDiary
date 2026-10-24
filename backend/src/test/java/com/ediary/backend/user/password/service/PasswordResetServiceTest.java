package com.ediary.backend.user.password.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.notification.service.EmailService;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.password.entity.PasswordResetToken;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.password.repository.PasswordResetTokenRepository;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.session.repository.UserSessionRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class PasswordResetServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    final UserSessionRepository userSessions = mock(UserSessionRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final EmailService email = mock(EmailService.class);
    final SessionRegistry sessions = mock(SessionRegistry.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final TransactionStatus transaction = mock(TransactionStatus.class);
    final LocalDateTime now = LocalDateTime.of(2026, 9, 5, 12, 0);
    PasswordResetService service;
    AppUser user;

    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        service = new PasswordResetService(users, tokens, userSessions, new TransactionTemplate(manager),
            encoder, email, sessions, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
            "https://school.example", org.mockito.Mockito.mock(com.ediary.backend.common.audit.AuditService.class));
        user = new AppUser();
        user.setId(1L);
        user.setLogin("student.test");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("old-hash");
    }

    @Test
    void unknownEmailDoesNotCreateTokenOrSendMail() {
        when(users.findByEmailForUpdate("missing@example.com")).thenReturn(Optional.empty());
        service.request(" Missing@Example.com ");
        verifyNoInteractions(tokens, email);
    }

    @Test
    void storesOnlyHashAndSendsCommittedTokenInLink() {
        when(users.findByEmailForUpdate("student@example.com")).thenReturn(Optional.of(user));
        when(email.sendText(anyString(), anyString(), anyString())).thenReturn(true);
        service.request("student@example.com");
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        var order = inOrder(manager, email);
        order.verify(manager).commit(transaction);
        order.verify(email).sendText(eq("student@example.com"), anyString(), body.capture());
        String raw = body.getValue().split("token=")[1].split("\n")[0];
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        var savedToken = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(savedToken.capture());
        assertThat(savedToken.getValue().getUser()).isSameAs(user);
        assertThat(savedToken.getValue().getTokenHash()).isEqualTo(PasswordResetService.hash(raw));
        assertThat(savedToken.getValue().getExpiresAt()).isEqualTo(now.plusMinutes(30));
        assertThat(savedToken.getValue().getCreatedAt()).isEqualTo(now);
    }

    @Test
    void cooldownDoesNotSendAnotherEmail() {
        when(users.findByEmailForUpdate("student@example.com")).thenReturn(Optional.of(user));
        when(tokens.countByUser_IdAndCreatedAtAfter(1L, now.minusMinutes(2))).thenReturn(1L);
        service.request("student@example.com");
        verifyNoInteractions(email);
    }

    @Test
    void usedOrExpiredTokenCannotChangePassword() {
        when(tokens.findByTokenHash(PasswordResetService.hash("token"))).thenReturn(Optional.of(resetToken()));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.reset("token", "NewPassword123"))
            .isInstanceOf(ApplicationException.class).hasMessageContaining("Посилання недійсне");
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verifyNoInteractions(encoder, sessions);
        verify(manager).rollback(transaction);
    }

    @Test
    void resetHashesPasswordConsumesTokensAndExpiresSessionsAfterCommit() {
        when(tokens.findByTokenHash(PasswordResetService.hash("token"))).thenReturn(Optional.of(resetToken()));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(tokens.consume(PasswordResetService.hash("token"), now)).thenReturn(1);
        when(encoder.encode("NewPassword123")).thenReturn("new-hash");
        SessionInformation session = new SessionInformation("student.test", "session", new Date());
        when(sessions.getAllSessions("student.test", false)).thenReturn(List.of(session));
        service.reset("token", "NewPassword123");
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(session.isExpired()).isTrue();
        verify(tokens).markAllUnusedAsUsed(1L);
        verify(userSessions).revokeActiveSessions(1L, now, "PASSWORD_RESET");
        var order = inOrder(manager, sessions);
        order.verify(manager).commit(transaction);
        order.verify(sessions).getAllSessions("student.test", false);
    }

    private PasswordResetToken resetToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        return token;
    }
}
