package com.ediary.backend.user.profile.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ChangePasswordRequestValidationTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();

    @Test
    void rejectsNewPasswordWithoutLetters() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "12345678");

        assertThat(validator.validate(request))
            .anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("newPassword");
                assertThat(violation.getMessage()).isEqualTo("Пароль має містити літери та цифри");
            });
    }

    @Test
    void rejectsNewPasswordWithoutDigits() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword");

        assertThat(validator.validate(request))
            .anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("newPassword");
                assertThat(violation.getMessage()).isEqualTo("Пароль має містити літери та цифри");
            });
    }

    @Test
    void acceptsNewPasswordWithLettersAndDigits() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "НовийПароль123");

        assertThat(validator.validate(request)).isEmpty();
    }
}
