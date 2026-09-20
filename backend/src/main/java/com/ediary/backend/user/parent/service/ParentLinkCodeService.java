package com.ediary.backend.user.parent.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ParentLinkCodeService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATION_ATTEMPTS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudentProfileRepository studentProfileRepository;
    private final byte[] secret;

    public ParentLinkCodeService(
        StudentProfileRepository studentProfileRepository,
        @Value("${app.parent-link-code.secret}") String secret
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Parent link code secret must contain at least 32 characters");
        }
        this.studentProfileRepository = studentProfileRepository;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public GeneratedParentLinkCode generateUnique() {
        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            String code = randomGroup() + "-" + randomGroup();
            String hash = hash(code);
            if (!studentProfileRepository.existsByParentLinkCodeHash(hash)) {
                return new GeneratedParentLinkCode(code, hash);
            }
        }
        throw new IllegalStateException("Could not generate a unique parent link code");
    }

    public StudentProfile requireActiveStudent(String rawCode) {
        String normalizedCode = normalize(rawCode);
        return studentProfileRepository
            .findByParentLinkCodeHashAndUser_StatusAndStatus(
                hash(normalizedCode),
                UserStatus.ACTIVE,
                StudentStatus.STUDYING
            )
            .orElseThrow(this::invalidCode);
    }

    public String normalize(String rawCode) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}$")) {
            throw invalidCode();
        }
        return code;
    }

    private String randomGroup() {
        StringBuilder value = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is not available", exception);
        }
    }

    private ApplicationException invalidCode() {
        return new ApplicationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARENT_LINK_CODE",
            "Код прив’язки до дитини недійсний"
        );
    }

    public record GeneratedParentLinkCode(String value, String hash) {
    }
}
