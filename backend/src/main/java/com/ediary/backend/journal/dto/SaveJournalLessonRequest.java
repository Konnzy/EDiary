package com.ediary.backend.journal.dto;

import com.ediary.backend.journal.entity.LessonWorkType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SaveJournalLessonRequest(
    @NotNull LessonWorkType workType,
    @Size(max = 200) String topic,
    @Size(max = 500) String homework,
    LocalDate homeworkDeadline
) { }
