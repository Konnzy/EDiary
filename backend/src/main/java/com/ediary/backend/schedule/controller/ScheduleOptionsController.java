package com.ediary.backend.schedule.controller;

import com.ediary.backend.schedule.dto.response.ScheduleOptionsResponse;
import com.ediary.backend.schedule.service.UserScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasAnyRole('STUDENT', 'PARENT', 'TEACHER')")
@RequiredArgsConstructor
public class ScheduleOptionsController {

    private final UserScheduleService userScheduleService;

    @GetMapping("/options")
    public ScheduleOptionsResponse findOptions() {
        return userScheduleService.findOptions();
    }
}
