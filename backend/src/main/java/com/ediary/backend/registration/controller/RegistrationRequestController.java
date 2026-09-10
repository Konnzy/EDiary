package com.ediary.backend.registration.controller;

import com.ediary.backend.registration.dto.request.CreateRegistrationRequest;
import com.ediary.backend.registration.dto.response.RegistrationOptionsResponse;
import com.ediary.backend.registration.dto.response.RegistrationSubmissionResponse;
import com.ediary.backend.registration.service.RegistrationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registration-requests")
@RequiredArgsConstructor
public class RegistrationRequestController {

    private final RegistrationRequestService registrationRequestService;

    @GetMapping("/options")
    public RegistrationOptionsResponse getOptions() {
        return registrationRequestService.getOptions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationSubmissionResponse submit(
        @Valid @RequestBody CreateRegistrationRequest request
    ) {
        return registrationRequestService.submit(request);
    }
}
