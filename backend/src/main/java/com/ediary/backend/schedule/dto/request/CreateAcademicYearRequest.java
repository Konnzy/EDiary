package com.ediary.backend.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAcademicYearRequest(
    @NotBlank(message = "Вкажіть навчальний рік")
    @Pattern(regexp = "^\\d{4}/\\d{4}$", message = "Використайте формат РРРР/РРРР")
    String academicYear
) {
}
