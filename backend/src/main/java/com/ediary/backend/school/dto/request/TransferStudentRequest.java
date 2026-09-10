package com.ediary.backend.school.dto.request;

import jakarta.validation.constraints.NotNull;

public record TransferStudentRequest(
    @NotNull(message = "Оберіть новий клас") Long targetClassId
) {
}
