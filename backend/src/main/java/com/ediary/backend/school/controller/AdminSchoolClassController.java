package com.ediary.backend.school.controller;

import com.ediary.backend.school.dto.request.SaveSchoolClassRequest;
import com.ediary.backend.school.dto.request.TransferStudentRequest;
import com.ediary.backend.school.dto.response.AdminClassOptionsResponse;
import com.ediary.backend.school.dto.response.AdminClassStudentResponse;
import com.ediary.backend.school.dto.response.AdminSchoolClassResponse;
import com.ediary.backend.school.dto.response.ClassDeletionEligibilityResponse;
import com.ediary.backend.school.service.AdminSchoolClassService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/classes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSchoolClassController {

    private final AdminSchoolClassService service;

    @GetMapping
    public List<AdminSchoolClassResponse> findAll(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer number,
        @RequestParam(required = false) String academicYear
    ) {
        return service.findAll(query, number, academicYear);
    }

    @GetMapping("/options")
    public AdminClassOptionsResponse getOptions(@RequestParam(required = false) String academicYear) {
        return service.getOptions(academicYear);
    }

    @GetMapping("/{classId}")
    public AdminSchoolClassResponse findById(@PathVariable Long classId) {
        return service.findById(classId);
    }

    @GetMapping("/{classId}/deletion-eligibility")
    public ClassDeletionEligibilityResponse getDeletionEligibility(@PathVariable Long classId) {
        return service.getDeletionEligibility(classId);
    }

    @GetMapping("/{classId}/students")
    public List<AdminClassStudentResponse> findStudents(
        @PathVariable Long classId,
        @RequestParam(required = false) String query
    ) {
        return service.findStudents(classId, query);
    }

    @GetMapping("/students/unassigned")
    public List<AdminClassStudentResponse> findUnassignedStudents(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String academicYear
    ) {
        return service.findUnassignedStudents(query, academicYear);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSchoolClassResponse create(@Valid @RequestBody SaveSchoolClassRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{classId}")
    public AdminSchoolClassResponse update(
        @PathVariable Long classId,
        @Valid @RequestBody SaveSchoolClassRequest request
    ) {
        return service.update(classId, request);
    }

    @PostMapping("/{classId}/students/{studentId}")
    public AdminClassStudentResponse addStudent(
        @PathVariable Long classId,
        @PathVariable Long studentId
    ) {
        return service.addStudent(classId, studentId);
    }

    @PatchMapping("/{classId}/students/{studentId}/class")
    public AdminClassStudentResponse transferStudent(
        @PathVariable Long classId,
        @PathVariable Long studentId,
        @Valid @RequestBody TransferStudentRequest request
    ) {
        return service.transferStudent(classId, studentId, request.targetClassId());
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStudent(
        @PathVariable Long classId,
        @PathVariable Long studentId
    ) {
        service.removeStudent(classId, studentId);
    }

    @DeleteMapping("/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long classId) {
        service.delete(classId);
    }
}
