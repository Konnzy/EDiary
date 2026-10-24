package com.ediary.backend.notification.controller;

import com.ediary.backend.notification.dto.UserNotificationFeedResponse;
import com.ediary.backend.notification.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('STUDENT', 'PARENT', 'TEACHER')")
@RequiredArgsConstructor
public class UserNotificationController {
    private final UserNotificationService service;

    @GetMapping
    public UserNotificationFeedResponse feed(Authentication authentication) {
        return service.feed(authentication.getName());
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, Authentication authentication) {
        service.markRead(authentication.getName(), id);
    }

    @PostMapping("/read-all")
    public void markAllRead(Authentication authentication) {
        service.markAllRead(authentication.getName());
    }

    @DeleteMapping("/{id}")
    public void dismiss(@PathVariable Long id, Authentication authentication) {
        service.dismiss(authentication.getName(), id);
    }
}
