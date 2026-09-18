package com.ediary.backend.school.controller;

import com.ediary.backend.school.dto.request.UpdateLessonRequest;
import com.ediary.backend.school.dto.response.LessonResponse;
import com.ediary.backend.school.service.LessonService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/lessons")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherLessonController {

    private final LessonService lessonService;

    @PutMapping("/schedule/{scheduleId}")
    public LessonResponse open(
        @PathVariable Long scheduleId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return lessonService.openForTeacher(authentication.getName(), scheduleId, date);
    }

    @GetMapping
    public List<LessonResponse> findAllByDate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return lessonService.findAllForTeacherByDate(authentication.getName(), date);
    }

    @GetMapping("/{lessonId}")
    public LessonResponse find(
        @PathVariable Long lessonId,
        Authentication authentication
    ) {
        return lessonService.findForTeacher(authentication.getName(), lessonId);
    }

    @PutMapping("/{lessonId}")
    public LessonResponse update(
        @PathVariable Long lessonId,
        @Valid @RequestBody UpdateLessonRequest request,
        Authentication authentication
    ) {
        return lessonService.updateForTeacher(authentication.getName(), lessonId, request);
    }
}
