package com.ediary.backend.journal.controller;

import com.ediary.backend.journal.dto.JournalOptionsResponse;
import com.ediary.backend.journal.dto.JournalResponse;
import com.ediary.backend.journal.dto.SaveJournalCellRequest;
import com.ediary.backend.journal.dto.SaveJournalLessonRequest;
import com.ediary.backend.journal.dto.SubjectProgressResponse;
import com.ediary.backend.journal.service.JournalService;
import com.ediary.backend.journal.service.SubjectProgressService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;
    private final SubjectProgressService subjectProgressService;

    @GetMapping("/teacher/journal/options")
    @PreAuthorize("hasRole('TEACHER')")
    public JournalOptionsResponse options(
        @RequestParam(required = false) String academicYear,
        Authentication authentication
    ) {
        return journalService.options(authentication.getName(), academicYear);
    }

    @GetMapping("/teacher/journal")
    @PreAuthorize("hasRole('TEACHER')")
    public JournalResponse journal(
        @RequestParam Long classId,
        @RequestParam Long subjectId,
        @RequestParam String month,
        Authentication authentication
    ) {
        return journalService.find(authentication.getName(), classId, subjectId, month);
    }

    @PutMapping("/teacher/journal/lessons/schedule/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TEACHER')")
    public void saveLesson(
        @PathVariable Long scheduleId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Valid @RequestBody SaveJournalLessonRequest request,
        Authentication authentication
    ) {
        journalService.saveLesson(authentication.getName(), scheduleId, date, request);
    }

    @PutMapping("/teacher/journal/lessons/{lessonId}/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TEACHER')")
    public void saveCell(
        @PathVariable Long lessonId,
        @PathVariable Long studentId,
        @Valid @RequestBody SaveJournalCellRequest request,
        Authentication authentication
    ) {
        journalService.saveCell(authentication.getName(), lessonId, studentId, request);
    }

    @PutMapping("/teacher/journal/lessons/schedule/{scheduleId}/attendance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TEACHER')")
    public void markAllPresent(
        @PathVariable Long scheduleId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        journalService.markAllPresent(authentication.getName(), scheduleId, date);
    }

    @DeleteMapping("/teacher/journal/lessons/{lessonId}/attendance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TEACHER')")
    public void clearAttendance(@PathVariable Long lessonId, Authentication authentication) {
        journalService.clearAttendance(authentication.getName(), lessonId);
    }

    @GetMapping("/student/subjects")
    @PreAuthorize("hasRole('STUDENT')")
    public SubjectProgressResponse studentSubjects(
        @RequestParam(required = false) String academicYear,
        Authentication authentication
    ) {
        return subjectProgressService.forStudent(authentication.getName(), academicYear);
    }

    @GetMapping("/parent/children/{studentId}/subjects")
    @PreAuthorize("hasRole('PARENT')")
    public SubjectProgressResponse parentSubjects(
        @PathVariable Long studentId,
        @RequestParam(required = false) String academicYear,
        Authentication authentication
    ) {
        return subjectProgressService.forParent(authentication.getName(), studentId, academicYear);
    }
}
