package com.ediary.backend.user.profile.controller;

import com.ediary.backend.user.profile.dto.ChangePasswordRequest;
import com.ediary.backend.user.profile.dto.ProfileDetailsResponse;
import com.ediary.backend.user.profile.dto.UpdateNotificationPreferencesRequest;
import com.ediary.backend.user.profile.dto.UpdatePhoneRequest;
import com.ediary.backend.user.profile.entity.UserAvatar;
import com.ediary.backend.user.profile.service.ProfileService;
import com.ediary.backend.user.profile.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('STUDENT', 'PARENT', 'TEACHER')")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;

    @GetMapping("/me")
    public ProfileDetailsResponse me(Authentication authentication) {
        return profileService.find(authentication.getName());
    }

    @PatchMapping("/me/phone")
    public ProfileDetailsResponse updatePhone(@Valid @RequestBody UpdatePhoneRequest request, Authentication authentication) {
        return profileService.updatePhone(authentication.getName(), request.phone());
    }

    @PutMapping("/me/notifications")
    public ProfileDetailsResponse updateNotifications(
        @RequestBody UpdateNotificationPreferencesRequest request,
        Authentication authentication
    ) {
        return profileService.updateNotifications(authentication.getName(), request);
    }

    @PostMapping("/me/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        userService.changeProfilePassword(authentication.getName(), request);
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> avatar(Authentication authentication) {
        UserAvatar avatar = profileService.findAvatar(authentication.getName());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(avatar.getContentType()))
            .body(avatar.getImageData());
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileDetailsResponse uploadAvatar(@RequestPart(value = "file", required = false) MultipartFile file, Authentication authentication) {
        return profileService.uploadAvatar(authentication.getName(), file);
    }

    @DeleteMapping("/me/avatar")
    public void deleteAvatar(Authentication authentication) {
        profileService.deleteAvatar(authentication.getName());
    }
}
