package com.ediary.backend.school.dto.request;

import com.ediary.backend.school.entity.Weekday;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveScheduleLessonRequest(
    @NotNull(message = "Вкажіть день тижня") Weekday weekday,
    @NotNull(message = "Вкажіть номер уроку")
    @Min(value = 1, message = "Номер уроку має бути від 1 до 8")
    @Max(value = 8, message = "Номер уроку має бути від 1 до 8")
    Integer lessonNumber,
    @NotNull(message = "Оберіть предмет") Long subjectId,
    @NotNull(message = "Оберіть учителя") Long teacherId,
    @NotBlank(message = "Вкажіть кабінет")
    @Size(max = 20, message = "Назва кабінету має містити не більше 20 символів")
    String room
) {
}
