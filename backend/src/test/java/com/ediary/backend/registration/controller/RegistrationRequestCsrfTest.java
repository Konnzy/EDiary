package com.ediary.backend.registration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ediary.backend.registration.dto.response.RegistrationSubmissionResponse;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.service.RegistrationRequestService;
import com.ediary.backend.security.SecurityConfig;
import com.ediary.backend.security.SecurityController;
import com.ediary.backend.security.SecurityErrorHandler;
import com.ediary.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({RegistrationRequestController.class, SecurityController.class})
@ContextConfiguration(classes = {
    RegistrationRequestController.class, SecurityController.class, SecurityConfig.class, SecurityErrorHandler.class
})
class RegistrationRequestCsrfTest {

    private static final String REGISTRATION = "/api/registration-requests";
    private static final String REQUEST = """
        {
            "firstName": "Лук’ян",
            "lastName": "Квітка-Основ’яненко",
            "patronymic": "",
            "email": "student@example.com",
            "phone": "+380671234567",
            "birthDate": "2010-05-12",
            "role": "STUDENT",
            "requestedClassId": 5,
            "termsAccepted": true
        }
        """;

    private final MockMvc mvc;

    @MockitoBean
    private RegistrationRequestService registrationRequests;

    @MockitoBean
    private UserRepository users;

    @Autowired
    RegistrationRequestCsrfTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void rejectsAnonymousRegistrationWithoutCsrfToken() throws Exception {
        mvc.perform(post(REGISTRATION).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void acceptsAnonymousRegistrationWithTokenFromPublicCsrfEndpoint() throws Exception {
        when(registrationRequests.submit(any())).thenReturn(new RegistrationSubmissionResponse(
            1L, RegistrationStatus.PENDING, "Заявку подано", LocalDateTime.of(2026, 1, 1, 12, 0)
        ));

        var csrf = mvc.perform(get("/api/security/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("XSRF-TOKEN", true))
            .andReturn()
            .getResponse();
        var payload = JsonPath.parse(csrf.getContentAsString());
        String headerName = payload.read("$.headerName");
        String token = payload.read("$.token");

        mvc.perform(post(REGISTRATION)
                .cookie(csrf.getCookie("XSRF-TOKEN"))
                .header(headerName, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1));
    }
}
