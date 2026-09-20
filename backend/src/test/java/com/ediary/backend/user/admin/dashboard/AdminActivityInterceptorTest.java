package com.ediary.backend.user.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
class AdminActivityInterceptorTest {

    @Mock private AdminActivityRecorder recorder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsSuccessfulAdministratorChange() {
        authenticate("ROLE_ADMIN");
        MockHttpServletRequest request = request("DELETE", "/api/admin/classes/{classId}");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("classId", "9"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);
        AdminActivityInterceptor interceptor = new AdminActivityInterceptor(recorder);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<AdminActivityAction> action = ArgumentCaptor.forClass(AdminActivityAction.class);
        verify(recorder).record(eq("admin"), action.capture());
        assertThat(action.getValue()).isEqualTo(new AdminActivityAction("Видалено клас", "Клас #9"));
    }

    @Test
    void ignoresFailedAndNonAdminChanges() {
        authenticate("ROLE_ADMIN");
        MockHttpServletRequest failed = request("PUT", "/api/admin/schedules");
        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        errorResponse.setStatus(400);
        AdminActivityInterceptor interceptor = new AdminActivityInterceptor(recorder);
        interceptor.preHandle(failed, errorResponse, new Object());
        interceptor.afterCompletion(failed, errorResponse, new Object(), null);

        SecurityContextHolder.clearContext();
        authenticate("ROLE_STUDENT");
        MockHttpServletRequest student = request("POST", "/api/admin/users");
        MockHttpServletResponse okResponse = new MockHttpServletResponse();
        interceptor.preHandle(student, okResponse, new Object());
        interceptor.afterCompletion(student, okResponse, new Object(), null);

        verify(recorder, never()).record(any(), any());
    }

    private MockHttpServletRequest request(String method, String pattern) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, pattern);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        return request;
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority(authority))
            )
        );
    }
}
