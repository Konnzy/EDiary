package com.ediary.backend.school.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SaveSchoolClassRequest(
    @NotNull(message = "Вкажіть номер класу")
    @Min(value = 1, message = "Номер класу має бути від 1 до 12")
    @Max(value = 12, message = "Номер класу має бути від 1 до 12")
    Integer number,

    @NotBlank(message = "Вкажіть літеру класу")
    @Pattern(regexp = "^[А-ЯІЇЄҐ]$", message = "Літера класу має бути однією великою українською літерою")
    String letter,

    Long classTeacherId,

    @NotBlank(message = "Вкажіть навчальний рік")
    @Pattern(regexp = "^\\d{4}/\\d{4}$", message = "Навчальний рік має бути у форматі РРРР/РРРР")
    String academicYear
) {
}
