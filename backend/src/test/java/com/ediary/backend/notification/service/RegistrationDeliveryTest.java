package com.ediary.backend.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ediary.backend.notification.listener.RegistrationNotificationListener;
import com.ediary.backend.registration.dto.response.RegistrationDecisionResponse;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.event.RegistrationApprovedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RegistrationDeliveryTest {
    @Test
    void successfulDeliveryPreservesTheApprovalMessage() {
        EmailService email = mock(EmailService.class);
        when(email.sendText(anyString(), anyString(), anyString())).thenReturn(true);
        var response = new RegistrationDecisionResponse(1L, RegistrationStatus.APPROVED,
            "teacher", LocalDateTime.now(), "Заявку схвалено");
        var event = new RegistrationApprovedEvent("teacher@example.com", "Учитель", "teacher",
            "temporary", "LINK42", response::setEmailSent);
        new RegistrationNotificationListener(email).sendApprovalEmail(event);
        assertTrue(response.getEmailSent());
        assertEquals("Заявку схвалено", response.getMessage());
        verify(email).sendText(eq("teacher@example.com"), anyString(), contains("LINK42"));
    }

    @Test
    void failedDeliveryPreservesAccountDecisionAndReportsFailure() {
        EmailService email = mock(EmailService.class);
        var response = new RegistrationDecisionResponse(1L, RegistrationStatus.APPROVED,
            "teacher", LocalDateTime.now(), "Заявку схвалено");
        var event = new RegistrationApprovedEvent("teacher@example.com", "Учитель", "teacher",
            "temporary", null, response::setEmailSent);
        new RegistrationNotificationListener(email).sendApprovalEmail(event);
        assertFalse(response.getEmailSent());
        assertEquals(RegistrationStatus.APPROVED,response.status());
        assertTrue(response.getMessage().contains("лист не надіслано"));
    }
}
