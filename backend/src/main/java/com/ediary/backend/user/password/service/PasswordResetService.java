package com.ediary.backend.user.password.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.notification.service.EmailService;
import com.ediary.backend.user.password.entity.PasswordResetToken;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.password.repository.PasswordResetTokenRepository;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.session.repository.UserSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final UserSessionRepository userSessions;
    private final TransactionTemplate transactions;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final SessionRegistry sessions;
    private final Clock clock;
    private final String frontendUrl;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private record ResetLink(String token, boolean administrator) {}

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
        UserSessionRepository userSessions, TransactionTemplate transactions, PasswordEncoder encoder,
        EmailService email, SessionRegistry sessions, Clock clock,
        @Value("${app.password-reset.frontend-url:http://127.0.0.1:5174}") String frontendUrl, AuditService audit) {
        this.users = users;
        this.tokens = tokens;
        this.userSessions = userSessions;
        this.transactions = transactions;
        this.encoder = encoder;
        this.email = email;
        this.sessions = sessions;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.audit = audit;
    }

    public void request(String address) {
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        ResetLink link = transactions.execute(transaction -> {
            var user = users.findByEmailForUpdate(normalized).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) return null;
            LocalDateTime now = LocalDateTime.now(clock);
            if (tokens.countByUser_IdAndCreatedAtAfter(user.getId(), now.minusMinutes(2)) > 0) return null;
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(hash(raw));
            resetToken.setExpiresAt(now.plusMinutes(30));
            resetToken.setCreatedAt(now);
            tokens.save(resetToken);
            audit.record("PASSWORD_RESET_REQUESTED", "USER", user.getId(), of(), of("created", true));
            return new ResetLink(raw, user.getRole() == UserRole.ADMIN);
        });
        if (link != null) {
            // Send only after the token transaction has committed.
            boolean sent = email.sendText(normalized, "Відновлення пароля електронного щоденника",
                "Для встановлення нового пароля відкрийте посилання:\n\n"
                + frontendUrl + (link.administrator() ? "/admin/reset-password?token=" : "/reset-password?token=")
                + link.token()
                + "\n\nПосилання діє 30 хвилин і може бути використане лише один раз."
                + "\nЯкщо ви не запитували зміну пароля, проігноруйте цей лист.");
            if (!sent) tokens.deleteByTokenHash(hash(link.token()));
        }
    }

    public void reset(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                "Пароль надто довгий: максимум 72 байти у UTF-8");
        }
        String login = transactions.execute(transaction -> {
            String tokenHash = hash(token);
            PasswordResetToken resetToken = tokens.findByTokenHash(tokenHash).orElseThrow(this::invalidToken);
            // All resets for one account serialize on the same user row.
            var user = users.findByIdForUpdate(resetToken.getUser().getId()).orElseThrow(this::invalidToken);
            if (user.getStatus() != UserStatus.ACTIVE) throw invalidToken();
            LocalDateTime now = LocalDateTime.now(clock);
            int consumed = tokens.consume(tokenHash, now);
            if (consumed != 1) throw invalidToken();
            user.setPasswordHash(encoder.encode(password));
            user.setMustChangePassword(false);
            user.setUpdatedAt(now);
            users.saveAndFlush(user);
            tokens.markAllUnusedAsUsed(user.getId());
            userSessions.revokeActiveSessions(user.getId(), now, "PASSWORD_RESET");
            audit.record("PASSWORD_RESET_COMPLETED", "USER", user.getId(), of(), of("passwordChanged", true));
            return user.getLogin();
        });
        sessions.getAllSessions(login, false).forEach(session -> session.expireNow());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApplicationException invalidToken() {
        return new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
            "Посилання недійсне або термін його дії минув. Запитайте нове посилання.");
    }
}
