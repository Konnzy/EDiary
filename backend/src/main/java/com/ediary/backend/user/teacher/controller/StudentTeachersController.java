package com.ediary.backend.user.teacher.controller;

import com.ediary.backend.user.teacher.dto.StudentTeachersResponse;
import com.ediary.backend.user.teacher.service.StudentTeachersService;
import lombok.RequiredArgsConstructor;
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
}
