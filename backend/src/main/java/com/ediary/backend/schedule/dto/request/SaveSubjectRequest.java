package com.ediary.backend.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record SaveSubjectRequest(
    @NotBlank(message = "Вкажіть назву предмета")
    @Size(min = 2, max = 50, message = "Назва предмета має містити від 2 до 50 символів")
    String title,

    @NotNull(message = "Передайте список учителів")
    Set<@NotNull(message = "Некоректний ідентифікатор учителя") Long> teacherIds
) {
}
