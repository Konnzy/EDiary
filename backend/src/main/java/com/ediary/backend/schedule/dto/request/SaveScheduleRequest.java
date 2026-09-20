package com.ediary.backend.schedule.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveScheduleRequest(
    @NotNull(message = "Передайте список уроків")
    @Size(max = 40, message = "Розклад може містити не більше 40 уроків")
    List<@Valid SaveScheduleLessonRequest> lessons
) {
}
