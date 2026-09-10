package com.ediary.backend.user.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.common.mail.EmailService;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.UserRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final SessionRegistry sessions;
    private final Clock clock;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, JdbcTemplate jdbc, TransactionTemplate transactions,
        PasswordEncoder encoder, EmailService email, SessionRegistry sessions, Clock clock,
        @Value("${app.password-reset.frontend-url:http://127.0.0.1:5174}") String frontendUrl) {
        this.users = users;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.encoder = encoder;
        this.email = email;
        this.sessions = sessions;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void request(String address) {
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        String token = transactions.execute(transaction -> {
            var user = users.findByEmailForUpdate(normalized).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) return null;
            LocalDateTime now = LocalDateTime.now(clock);
            Integer recent = jdbc.queryForObject(
                "select count(*) from password_reset_token where user_id = ? and token_created_at > ?",
                Integer.class, user.getId(), now.minusMinutes(2));
            if (recent != null && recent > 0) return null;
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("insert into password_reset_token (user_id, token_hash, token_expires_at, token_created_at) values (?, ?, ?, ?)",
                user.getId(), hash(raw), now.plusMinutes(30), now);
            return raw;
        });
        if (token != null) {
            // Send only after the token transaction has committed.
            boolean sent = email.sendText(normalized, "Відновлення пароля електронного щоденника",
                "Для встановлення нового пароля відкрийте посилання:\n\n"
                + frontendUrl + "/#reset-password?token=" + token
                + "\n\nПосилання діє 30 хвилин і може бути використане лише один раз."
                + "\nЯкщо ви не запитували зміну пароля, проігноруйте цей лист.");
            if (!sent) jdbc.update("delete from password_reset_token where token_hash = ?", hash(token));
        }
    }

    public void reset(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                "Пароль надто довгий: максимум 72 байти у UTF-8");
        }
        String login = transactions.execute(transaction -> {
            String tokenHash = hash(token);
            var ids = jdbc.queryForList("select user_id from password_reset_token where token_hash = ?", Long.class, tokenHash);
            if (ids.isEmpty()) throw invalidToken();
            // All resets for one account serialize on the same user row.
            var user = users.findByIdForUpdate(ids.getFirst()).orElseThrow(this::invalidToken);
            if (user.getStatus() != UserStatus.ACTIVE) throw invalidToken();
            LocalDateTime now = LocalDateTime.now(clock);
            int consumed = jdbc.update("update password_reset_token set token_used = true where token_hash = ? and token_used = false and token_expires_at > ?",
                tokenHash, now);
            if (consumed != 1) throw invalidToken();
            user.setPasswordHash(encoder.encode(password));
            user.setMustChangePassword(false);
            user.setUpdatedAt(now);
            users.saveAndFlush(user);
            jdbc.update("update password_reset_token set token_used = true where user_id = ? and token_used = false", user.getId());
            jdbc.update("update user_session set session_revoked_at = ?, session_revoked_reason = 'PASSWORD_RESET' where user_id = ? and session_revoked_at is null", now, user.getId());
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
