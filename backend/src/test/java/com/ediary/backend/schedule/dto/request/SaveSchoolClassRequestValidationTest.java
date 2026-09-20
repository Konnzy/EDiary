package com.ediary.backend.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SaveSchoolClassRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidUkrainianClassName() {
        var violations = validator.validate(new SaveSchoolClassRequest(12, "Ї", null, "2026/2027"));

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsClassNumberOutsideRange() {
        var violations = validator.validate(new SaveSchoolClassRequest(13, "А", null, "2026/2027"));

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("number"));
    }

    @Test
    void rejectsLowercaseOrLatinLetter() {
        var lowercase = validator.validate(new SaveSchoolClassRequest(1, "а", null, "2026/2027"));
        var latin = validator.validate(new SaveSchoolClassRequest(1, "A", null, "2026/2027"));

        assertThat(lowercase).anyMatch(violation -> violation.getPropertyPath().toString().equals("letter"));
        assertThat(latin).anyMatch(violation -> violation.getPropertyPath().toString().equals("letter"));
    }
}
