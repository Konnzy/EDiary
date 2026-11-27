package com.ediary.backend.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final GmailApiMailSender gmailApiMailSender;

    @Value("${app.mail.provider:smtp}")
    private String provider;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:no-reply@ediary.local}")
    private String fromAddress;

    @Value("${app.mail.from-name:Електронний щоденник}")
    private String fromName;

    public boolean isEnabled() { return enabled; }

    public boolean sendText(String recipient, String subject, String body) {
        if (!enabled) {
            log.warn("event=mail.skipped reason=disabled");
            return false;
        }

        try {
            if (!"smtp".equalsIgnoreCase(provider) && !"gmail-api".equalsIgnoreCase(provider)) {
                throw new IllegalStateException("Unknown mail provider");
            }
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
            if ("gmail-api".equalsIgnoreCase(provider)) {
                gmailApiMailSender.send(message);
            } else {
                mailSender.send(message);
            }
            log.info("event=mail.accepted provider={}", provider);
            return true;
        } catch (RestClientResponseException exception) {
            // Do not log the response body, tokens, message contents or recipient.
            log.error("event=mail.failed provider={} httpStatus={}", provider, exception.getStatusCode().value());
            return false;
        } catch (MessagingException | IOException | MailException | RestClientException | IllegalStateException exception) {
            log.error("event=mail.failed provider={} type={}", provider, exception.getClass().getSimpleName());
            return false;
        }
    }
}
