package com.ediary.backend.registration.mapper;

import com.ediary.backend.registration.dto.response.RegistrationOptionResponse;
import com.ediary.backend.registration.dto.response.RegistrationRequestResponse;
import com.ediary.backend.registration.dto.response.RequestedStudentResponse;
import com.ediary.backend.registration.entity.RegistrationRequest;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRequestMapper {

    public RegistrationRequestResponse toResponse(RegistrationRequest request) {
        RegistrationOptionResponse requestedClass = request.getRequestedClass() == null
            ? null
            : new RegistrationOptionResponse(
                request.getRequestedClass().getId(),
                request.getRequestedClass().getDisplayName()
            );
        RegistrationOptionResponse requestedSubject = request.getRequestedSubject() == null
            ? null
            : new RegistrationOptionResponse(
                request.getRequestedSubject().getId(),
                request.getRequestedSubject().getTitle()
            );
        RequestedStudentResponse requestedStudent = request.getRequestedStudent() == null
            ? null
            : new RequestedStudentResponse(
                request.getRequestedStudent().getId(),
                request.getRequestedStudent().getName() + " " + request.getRequestedStudent().getSurname(),
                request.getRequestedStudent().getUser().getLogin(),
                request.getRequestedStudent().getSchoolClass() == null
                    ? null
                    : request.getRequestedStudent().getSchoolClass().getDisplayName()
            );

        return new RegistrationRequestResponse(
            request.getId(),
            request.getName(),
            request.getSurname(),
            request.getPatronymic(),
            request.getEmail(),
            request.getPhone(),
            request.getBirthDate(),
            request.getRequestedRole(),
            request.getStatus(),
            requestedClass,
            requestedSubject,
            requestedStudent,
            request.getCreatedAt(),
            request.getReviewedAt(),
            request.getReviewedBy() == null ? null : request.getReviewedBy().getLogin(),
            request.getRejectionReason(),
            request.getProcessedUser() == null ? null : request.getProcessedUser().getLogin()
        );
    }
}
