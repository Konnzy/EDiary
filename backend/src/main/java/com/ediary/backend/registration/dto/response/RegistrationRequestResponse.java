package com.ediary.backend.registration.dto.response;

import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.user.entity.UserRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RegistrationRequestResponse(
    Long id,
    String firstName,
    String lastName,
    String patronymic,
    String email,
    String phone,
    LocalDate birthDate,
    UserRole role,
    RegistrationStatus status,
    RegistrationOptionResponse requestedClass,
    RegistrationOptionResponse requestedSubject,
    RequestedStudentResponse requestedStudent,
    LocalDateTime submittedAt,
    LocalDateTime reviewedAt,
    String reviewedByLogin,
    String rejectionReason,
    String createdUserLogin
) {
}
