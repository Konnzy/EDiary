package com.ediary.backend.user.teacher.controller;

import com.ediary.backend.user.teacher.dto.TeacherStudentResponse;
import com.ediary.backend.user.teacher.service.TeacherStudentsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/students")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherStudentsController {
    private final TeacherStudentsService service;

    @GetMapping
    public List<TeacherStudentResponse> find(Authentication authentication) {
        return service.find(authentication.getName());
    }
}
