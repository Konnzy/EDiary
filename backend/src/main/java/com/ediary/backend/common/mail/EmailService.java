package com.ediary.backend.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:no-reply@ediary.local}")
    private String fromAddress;

    @Value("${app.mail.from-name:Електронний щоденник}")
    private String fromName;

    public boolean sendText(String recipient, String subject, String body) {
        if (!enabled) {
            log.warn("Email delivery is disabled; message to {} was not sent", recipient);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                false,
                StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Email delivered to {} with subject '{}'", recipient, subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            log.error("Email delivery to {} failed", recipient, exception);
            return false;
        }
    }
}
