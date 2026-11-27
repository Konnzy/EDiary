package com.ediary.backend.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GmailApiMailSender {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
    private final RestClient client;
    private final Clock clock;
    private String accessToken;
    private Instant refreshAt = Instant.EPOCH;

    @Value("${app.mail.gmail.client-id:}")
    private String clientId;
    @Value("${app.mail.gmail.client-secret:}")
    private String clientSecret;
    @Value("${app.mail.gmail.refresh-token:}")
    private String refreshToken;

    @Autowired
    public GmailApiMailSender() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.clock = Clock.systemUTC();
    }

    GmailApiMailSender(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public void send(MimeMessage message) throws MessagingException, IOException {
        String token = getAccessToken();
        message.saveChanges();
        var bytes = new ByteArrayOutputStream();
        message.writeTo(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        var response = client.post().uri(SEND_URL)
            .headers(headers -> headers.setBearerAuth(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("raw", raw))
            .retrieve().body(SendResponse.class);
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new RestClientException("Gmail API did not confirm message acceptance");
        }
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && clock.instant().isBefore(refreshAt)) {
            return accessToken;
        }
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            throw new IllegalStateException("Gmail API credentials are missing");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        var response = client.post().uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form).retrieve().body(TokenResponse.class);
        if (response == null || response.access_token() == null || response.access_token().isBlank()
            || response.expires_in() <= 0) {
            throw new RestClientException("Google returned an invalid access token response");
        }
        accessToken = response.access_token();
        refreshAt = clock.instant().plusSeconds(Math.max(0, response.expires_in() - 60));
        return accessToken;
    }

    private record TokenResponse(String access_token, long expires_in) {}
    private record SendResponse(String id) {}
}
