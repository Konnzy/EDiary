package com.ediary.backend.user.controller;

import com.ediary.backend.user.dto.request.LinkChildRequest;
import com.ediary.backend.user.dto.response.ParentChildResponse;
import com.ediary.backend.school.dto.response.UserScheduleResponse;
import com.ediary.backend.school.service.UserScheduleService;
import com.ediary.backend.user.service.ParentAccessService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parent/children")
@PreAuthorize("hasRole('PARENT')")
@RequiredArgsConstructor
public class ParentController {

    private final ParentAccessService parentAccessService;
    private final UserScheduleService userScheduleService;

    @GetMapping
    public List<ParentChildResponse> findChildren(Authentication authentication) {
        return parentAccessService.findChildren(authentication.getName());
    }

    @PostMapping
    public ParentChildResponse linkChild(
        @Valid @RequestBody LinkChildRequest request,
        Authentication authentication
    ) {
        return parentAccessService.linkChild(authentication.getName(), request.code());
    }

    @GetMapping("/{studentId}")
    public ParentChildResponse findChild(
        @PathVariable Long studentId,
        Authentication authentication
    ) {
        return parentAccessService.findChild(authentication.getName(), studentId);
    }

    @GetMapping("/{studentId}/schedule")
    public UserScheduleResponse findSchedule(
        @PathVariable Long studentId,
        @RequestParam Integer week,
        Authentication authentication
    ) {
        return userScheduleService.findForParent(
            authentication.getName(),
            studentId,
            week
        );
    }
}
