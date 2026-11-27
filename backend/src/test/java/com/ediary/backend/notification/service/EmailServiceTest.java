package com.ediary.backend.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

class EmailServiceTest {
    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final GmailApiMailSender gmail = mock(GmailApiMailSender.class);
    private final EmailService service = new EmailService(smtp, gmail);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "provider", "gmail-api");
        ReflectionTestUtils.setField(service, "fromAddress", "sender@example.com");
        ReflectionTestUtils.setField(service, "fromName", "Електронний щоденник");
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void gmailModeNeverSendsUsingSmtp() throws Exception {
        assertTrue(service.sendText("teacher@example.com", "Доступ", "Лист"));
        verify(gmail).send(any(MimeMessage.class));
        verify(smtp, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpModeStillWorks() throws Exception {
        ReflectionTestUtils.setField(service, "provider", "smtp");
        assertTrue(service.sendText("teacher@example.com", "Доступ", "Лист"));
        verify(smtp).send(any(MimeMessage.class));
        verifyNoInteractions(gmail);
    }

    @Test
    void disabledDeliveryDoesNotContactProvider() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertFalse(service.sendText("teacher@example.com", "Доступ", "Лист"));
        verifyNoInteractions(gmail);
        verify(smtp, never()).createMimeMessage();
    }

    @Test
    void apiFailureIsReportedAsUnsent() throws Exception {
        doThrow(new RestClientException("test failure")).when(gmail).send(any(MimeMessage.class));
        assertFalse(service.sendText("teacher@example.com", "Доступ", "Лист"));
        verify(smtp, never()).send(any(MimeMessage.class));
    }

    @Test
    void invalidProviderDoesNotSendMail() {
        ReflectionTestUtils.setField(service,"provider","invalid");
        assertFalse(service.sendText("teacher@example.com","Доступ","Лист"));
        verifyNoInteractions(gmail);
        verify(smtp,never()).send(any(MimeMessage.class));
    }

    @Test
    void rejectedApiRequestDoesNotFallBackToSmtp() throws Exception {
        doThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN)).when(gmail).send(any(MimeMessage.class));
        assertFalse(service.sendText("teacher@example.com","Доступ","Лист"));
        verify(smtp,never()).send(any(MimeMessage.class));
    }
}
