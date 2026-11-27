package com.ediary.backend.registration.dto.response;

import com.ediary.backend.registration.entity.RegistrationStatus;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RegistrationDecisionResponse {
    private final Long id;
    private final RegistrationStatus status;
    private final String createdUserLogin;
    private final LocalDateTime reviewedAt;
    private final String message;
    private Boolean emailSent;

    public Long id() { return id; }
    public RegistrationStatus status() { return status; }
    public String createdUserLogin() { return createdUserLogin; }
    public LocalDateTime reviewedAt() { return reviewedAt; }
    public String message() { return getMessage(); }

    public void setEmailSent(boolean sent) { this.emailSent = sent; }

    public String getMessage() {
        if (Boolean.FALSE.equals(emailSent)) {
            return "Заявку схвалено, акаунт створено, але лист не надіслано. Перевірте налаштування пошти та скористайтеся скиданням пароля користувача.";
        }
        return message;
    }
}
