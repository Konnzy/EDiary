package com.ediary.backend.school.dto.request;

import com.ediary.backend.school.entity.LessonStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLessonRequest(
    @Size(max = 200, message = "Тема уроку має містити не більше 200 символів")
    String topic,
    @NotNull(message = "Вкажіть статус уроку")
    LessonStatus status
) {
}
