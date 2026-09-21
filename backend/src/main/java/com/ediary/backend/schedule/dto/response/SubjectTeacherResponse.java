package com.ediary.backend.schedule.dto.response;

import com.ediary.backend.user.entity.UserStatus;
import java.util.List;

public record SubjectTeacherResponse(
    Long id,
    String fullName,
    UserStatus status,
    List<Long> subjectIds,
    List<String> subjectTitles,
    List<String> classNames
) {
}
