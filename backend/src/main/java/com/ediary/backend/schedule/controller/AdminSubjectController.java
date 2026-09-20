package com.ediary.backend.schedule.controller;

import com.ediary.backend.schedule.dto.request.SaveSubjectRequest;
import com.ediary.backend.schedule.dto.response.AdminSubjectOptionsResponse;
import com.ediary.backend.schedule.dto.response.AdminSubjectResponse;
import com.ediary.backend.schedule.dto.response.SubjectDeletionEligibilityResponse;
import com.ediary.backend.schedule.service.AdminSubjectService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subjects")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final AdminSubjectService service;

    @GetMapping
    public List<AdminSubjectResponse> findAll(@RequestParam(required = false) String query) {
        return service.findAll(query);
    }

    @GetMapping("/options")
    public AdminSubjectOptionsResponse getOptions() {
        return service.getOptions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSubjectResponse create(@Valid @RequestBody SaveSubjectRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{subjectId}")
    public AdminSubjectResponse update(
        @PathVariable Long subjectId,
        @Valid @RequestBody SaveSubjectRequest request
    ) {
        return service.update(subjectId, request);
    }

    @GetMapping("/{subjectId}/deletion-eligibility")
    public SubjectDeletionEligibilityResponse getDeletionEligibility(@PathVariable Long subjectId) {
        return service.getDeletionEligibility(subjectId);
    }

    @DeleteMapping("/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long subjectId) {
        service.delete(subjectId);
    }
}
