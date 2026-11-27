package com.ediary.backend.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

class GmailApiMailSenderTest {
    private MockRestServiceServer server;
    private GmailApiMailSender sender;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-27T12:00:00Z"));

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        sender = new GmailApiMailSender(builder.build(), clock);
        ReflectionTestUtils.setField(sender, "clientId", "test-client");
        ReflectionTestUtils.setField(sender, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(sender, "refreshToken", "test-refresh");
    }

    private MimeMessage message() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom("sender@example.com", "Електронний щоденник");
        helper.setTo("teacher@example.com");
        helper.setSubject("Доступ схвалено");
        helper.setText("Тимчасовий пароль: приклад", false);
        return message;
    }

    private void expectToken() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=test-secret")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=test-refresh")))
            .andRespond(withSuccess("{\"access_token\":\"test-access\",\"expires_in\":3600,\"token_type\":\"Bearer\",\"scope\":\"gmail.send\"}", MediaType.APPLICATION_JSON));
    }

    private void expectSend() {
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-access"))
            .andExpect(request -> {
                String json = ((MockClientHttpRequest) request).getBodyAsString();
                String raw = JsonMapper.builder().build().readTree(json).get("raw").asString();
                try {
                    var decoded = new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
                    assertEquals("Доступ схвалено", decoded.getSubject());
                    assertEquals("teacher@example.com", decoded.getAllRecipients()[0].toString());
                    assertTrue(decoded.getContent().toString().contains("Тимчасовий пароль"));
                } catch (Exception e) { throw new AssertionError(e); }
            })
            .andRespond(withSuccess("{\"id\":\"message-id\",\"threadId\":\"thread-id\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsUkrainianMimeAndReusesAccessToken() throws Exception {
        expectToken();
        expectSend();
        expectSend();
        sender.send(message());
        sender.send(message());
        server.verify();
    }

    @Test
    void refreshesExpiredAccessToken() throws Exception {
        expectToken(); expectSend(); expectToken(); expectSend();
        sender.send(message());
        now.set(now.get().plusSeconds(3600));
        sender.send(message());
        server.verify();
    }

    @Test
    void rejectedRefreshTokenDoesNotAttemptDelivery() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\"}"));
        assertThrows(RestClientResponseException.class, () -> sender.send(message()));
        server.verify();
    }

    @Test
    void rejectsMissingCredentialsWithoutNetworkCall() {
        ReflectionTestUtils.setField(sender, "refreshToken", "");
        assertThrows(IllegalStateException.class, () -> sender.send(message()));
        server.verify();
    }

    @Test
    void rejectsIncompleteTokenResponsesInsteadOfSendingMail() {
        for (String json : new String[]{"{}", "{\"access_token\":\"\",\"expires_in\":3600}",
            "{\"access_token\":\"token\",\"expires_in\":0}"}) {
            setup();
            server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
            assertThrows(RestClientException.class,()->sender.send(message()));
            server.verify();
        }
    }

    @Test
    void doesNotReportSuccessWithoutAMessageId() {
        for (String json : new String[]{"{}", "{\"id\":\"\"}"}) {
            setup(); expectToken();
            server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
            assertThrows(RestClientException.class,()->sender.send(message()));
            server.verify();
        }
    }

    @Test
    void surfacesGoogleDeliveryRejectionWithoutRetryingTheMessage() {
        expectToken();
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(RestClientResponseException.class,()->sender.send(message()));
        server.verify();
    }
}
