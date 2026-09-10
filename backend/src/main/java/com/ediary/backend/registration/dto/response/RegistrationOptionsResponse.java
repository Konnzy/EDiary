package com.ediary.backend.registration.dto.response;

import java.util.List;

public record RegistrationOptionsResponse(
    List<RegistrationOptionResponse> classes,
    List<RegistrationOptionResponse> subjects
) {
}
