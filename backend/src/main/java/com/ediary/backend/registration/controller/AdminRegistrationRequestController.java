package com.ediary.backend.registration.controller;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.registration.dto.request.ApproveRegistrationRequest;
import com.ediary.backend.registration.dto.request.RejectRegistrationRequest;
import com.ediary.backend.registration.dto.response.RegistrationDecisionResponse;
import com.ediary.backend.registration.dto.response.RegistrationNotificationFeedResponse;
import com.ediary.backend.registration.dto.response.RegistrationRequestResponse;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.service.RegistrationRequestService;
import com.ediary.backend.user.entity.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/registration-requests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRegistrationRequestController {

    private final RegistrationRequestService registrationRequestService;

    @GetMapping
    public PageResponse<RegistrationRequestResponse> findAll(
        @RequestParam(required = false) RegistrationStatus status,
        @RequestParam(required = false) UserRole role,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return registrationRequestService.findAll(status, role, page, size);
    }

    @GetMapping("/notifications")
    public RegistrationNotificationFeedResponse notifications() {
        return registrationRequestService.findNotificationFeed();
    }

    @PostMapping("/{requestId}/approve")
    public RegistrationDecisionResponse approve(
        @PathVariable Long requestId,
        @Valid @RequestBody ApproveRegistrationRequest request,
        Authentication authentication
    ) {
        return registrationRequestService.approve(requestId, authentication.getName(), request.login());
    }

    @PostMapping("/{requestId}/reject")
    public RegistrationDecisionResponse reject(
        @PathVariable Long requestId,
        Authentication authentication,
        @Valid @RequestBody RejectRegistrationRequest request
    ) {
        return registrationRequestService.reject(requestId, authentication.getName(), request.reason());
    }
}
