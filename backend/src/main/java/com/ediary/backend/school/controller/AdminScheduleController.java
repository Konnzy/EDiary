package com.ediary.backend.school.controller;

import com.ediary.backend.school.dto.request.CreateAcademicYearRequest;
import com.ediary.backend.school.dto.request.SaveScheduleRequest;
import com.ediary.backend.school.dto.response.AdminScheduleResponse;
import com.ediary.backend.school.service.AdminScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/schedules")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final AdminScheduleService service;

    @GetMapping("/academic-years")
    public List<String> findAcademicYears() {
        return service.findAcademicYears();
    }

    @PostMapping("/academic-years")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createAcademicYear(
        @Valid @RequestBody CreateAcademicYearRequest request
    ) {
        return Map.of("academicYear", service.createAcademicYear(request.academicYear()));
    }

    @GetMapping
    public AdminScheduleResponse find(
        @RequestParam Long classId,
        @RequestParam String academicYear,
        @RequestParam Integer week
    ) {
        return service.find(classId, academicYear, week);
    }

    @PutMapping
    public AdminScheduleResponse save(
        @RequestParam Long classId,
        @RequestParam String academicYear,
        @RequestParam Integer week,
        @Valid @RequestBody SaveScheduleRequest request
    ) {
        return service.save(classId, academicYear, week, request);
    }
}
