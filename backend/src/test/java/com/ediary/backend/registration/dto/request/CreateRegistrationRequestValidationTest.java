package com.ediary.backend.registration.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.ediary.backend.user.entity.UserRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CreateRegistrationRequestValidationTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();

    @Test
    void rejectsLatinCharactersInFirstAndLastName() {
        CreateRegistrationRequest request = new CreateRegistrationRequest(
            "Oleksandr",
            "Korolenko",
            null,
            "student@example.com",
            "+380671234567",
            LocalDate.of(2010, 5, 12),
            UserRole.STUDENT,
            5L,
            null,
            null,
            true
        );

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("firstName", "lastName");
    }

    @Test
    void acceptsUkrainianNamesWithApostropheHyphenAndSpace() {
        CreateRegistrationRequest request = new CreateRegistrationRequest(
            "Лук’ян",
            "Квітка-Основ’яненко",
            "Олександрович",
            "student@example.com",
            "+380671234567",
            LocalDate.of(2010, 5, 12),
            UserRole.STUDENT,
            5L,
            null,
            null,
            true
        );

        assertThat(validator.validate(request)).isEmpty();
    }
}
