package com.ediary.backend.journal.controller;

import com.ediary.backend.journal.dto.TeacherDashboardResponse;
import com.ediary.backend.journal.service.TeacherDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/dashboard")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherDashboardController {

    private final TeacherDashboardService service;

    @GetMapping
    public TeacherDashboardResponse find(Authentication authentication) {
        return service.find(authentication.getName());
    }
}
