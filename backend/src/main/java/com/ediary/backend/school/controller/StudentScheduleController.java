package com.ediary.backend.school.controller;

import com.ediary.backend.school.dto.response.UserScheduleResponse;
import com.ediary.backend.school.service.UserScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/schedule")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentScheduleController {

    private final UserScheduleService userScheduleService;

    @GetMapping
    public UserScheduleResponse find(
        @RequestParam Integer week,
        Authentication authentication
    ) {
        return userScheduleService.findForStudent(authentication.getName(), week);
    }
}
