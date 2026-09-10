package com.ediary.backend.user.controller;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.user.dto.request.UpdateUserStatusRequest;
import com.ediary.backend.user.dto.request.CreateAdminUserRequest;
import com.ediary.backend.user.dto.response.AdminUserCreationResponse;
import com.ediary.backend.user.dto.response.AdminUserResponse;
import com.ediary.backend.user.dto.response.UserDeletionEligibilityResponse;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserCreationResponse create(@Valid @RequestBody CreateAdminUserRequest request) {
        return adminUserService.create(request);
    }

    @GetMapping
    public PageResponse<AdminUserResponse> findAll(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) UserRole role,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return adminUserService.findAll(query, role, status, page, size);
    }

    @PatchMapping("/{userId}/status")
    public AdminUserResponse changeStatus(
        @PathVariable Long userId,
        @Valid @RequestBody UpdateUserStatusRequest request,
        Authentication authentication
    ) {
        return adminUserService.changeStatus(userId, request.status(), authentication.getName());
    }

    @GetMapping("/{userId}/deletion-eligibility")
    public UserDeletionEligibilityResponse getDeletionEligibility(@PathVariable Long userId) {
        return adminUserService.getDeletionEligibility(userId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long userId, Authentication authentication) {
        adminUserService.delete(userId, authentication.getName());
    }
}
