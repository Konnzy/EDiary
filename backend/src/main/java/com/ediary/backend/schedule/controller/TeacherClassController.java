package com.ediary.backend.schedule.controller;

import com.ediary.backend.journal.dto.SubjectProgressResponse;
import com.ediary.backend.journal.service.SubjectProgressService;
import com.ediary.backend.schedule.dto.response.TeacherClassOptionResponse;
import com.ediary.backend.schedule.dto.response.TeacherClassResponse;
import com.ediary.backend.schedule.dto.response.UserScheduleResponse;
import com.ediary.backend.schedule.service.TeacherClassService;
import com.ediary.backend.schedule.service.UserScheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/my-classes")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherClassController {

    private final TeacherClassService teacherClassService;
    private final SubjectProgressService subjectProgressService;
    private final UserScheduleService userScheduleService;

    @GetMapping
    public List<TeacherClassOptionResponse> findClasses(Authentication authentication) {
        return teacherClassService.findClasses(authentication.getName());
    }

    @GetMapping("/{classId}")
    public TeacherClassResponse findClass(@PathVariable Long classId, Authentication authentication) {
        return teacherClassService.findClass(authentication.getName(), classId);
    }

    @GetMapping("/{classId}/schedule")
    public UserScheduleResponse schedule(
        @PathVariable Long classId,
        @RequestParam Integer week,
        Authentication authentication
    ) {
        return userScheduleService.findForHomeroomTeacher(authentication.getName(), classId, week);
    }

    @GetMapping("/{classId}/students/{studentId}/subjects")
    public SubjectProgressResponse studentSubjects(
        @PathVariable Long classId,
        @PathVariable Long studentId,
        @RequestParam(required = false) String academicYear,
        Authentication authentication
    ) {
        return subjectProgressService.forHomeroomTeacher(authentication.getName(), classId, studentId, academicYear);
    }
}
