package com.ediary.backend.registration.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ApproveRegistrationRequestValidationTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();

    @Test
    void acceptsLatinLettersDigitsAndSupportedSeparators() {
        assertThat(validator.validate(new ApproveRegistrationRequest("student-10_a.test"))).isEmpty();
    }

    @Test
    void rejectsCyrillicAndUnsupportedSymbols() {
        assertThat(validator.validate(new ApproveRegistrationRequest("учень@test"))).isNotEmpty();
    }

    @Test
    void rejectsSeparatorAtBeginningOrEnd() {
        assertThat(validator.validate(new ApproveRegistrationRequest("_student"))).isNotEmpty();
        assertThat(validator.validate(new ApproveRegistrationRequest("student."))).isNotEmpty();
    }
}
