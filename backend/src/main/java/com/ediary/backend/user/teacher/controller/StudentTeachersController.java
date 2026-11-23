package com.ediary.backend.user.teacher.controller;

import com.ediary.backend.user.profile.entity.UserAvatar;
import com.ediary.backend.user.teacher.dto.StudentTeachersResponse;
import com.ediary.backend.user.teacher.service.StudentTeachersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StudentTeachersController {
    private final StudentTeachersService service;

    @GetMapping("/student/teachers")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentTeachersResponse student(Authentication authentication) {
        return service.forStudent(authentication.getName());
    }

    @GetMapping("/parent/children/{studentId}/teachers")
    @PreAuthorize("hasRole('PARENT')")
    public StudentTeachersResponse parent(@PathVariable Long studentId, Authentication authentication) {
        return service.forParent(authentication.getName(), studentId);
    }

    @GetMapping("/student/teachers/{teacherId}/avatar")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> studentAvatar(@PathVariable Long teacherId, Authentication authentication) {
        return imageResponse(service.homeroomAvatarForStudent(authentication.getName(), teacherId));
    }

    @GetMapping("/parent/children/{studentId}/teachers/{teacherId}/avatar")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<byte[]> parentAvatar(@PathVariable Long studentId, @PathVariable Long teacherId, Authentication authentication) {
        return imageResponse(service.homeroomAvatarForParent(authentication.getName(), studentId, teacherId));
    }

    private ResponseEntity<byte[]> imageResponse(UserAvatar avatar) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(avatar.getContentType()))
            .body(avatar.getImageData());
    }
}
