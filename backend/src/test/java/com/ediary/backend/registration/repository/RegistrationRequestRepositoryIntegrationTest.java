package com.ediary.backend.registration.repository;

import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "app.bootstrap-admin.enabled=false"})
@EnabledIfEnvironmentVariable(named = "EDIARY_RUN_DB_TESTS", matches = "true")
@Transactional(readOnly = true)
class RegistrationRequestRepositoryIntegrationTest {
    @Autowired
    private RegistrationRequestRepository repository;

    @Test
    void filtersAndPendingPriorityExecuteAgainstPostgresEnums() {
        var page = PageRequest.of(0, 1);
        repository.findAllWithPendingPriority(page);
        for (var status : RegistrationStatus.values()) {
            repository.findAllWithPendingPriorityByStatus(status, page).forEach(request ->
                assertEquals(status, request.getStatus()));
            for (var role : new UserRole[]{UserRole.STUDENT, UserRole.PARENT, UserRole.TEACHER}) {
                repository.findAllWithPendingPriorityByStatusAndRole(status, role, page).forEach(request -> {
                    assertEquals(status, request.getStatus());
                    assertEquals(role, request.getRequestedRole());
                });
            }
        }
        for (var role : new UserRole[]{UserRole.STUDENT, UserRole.PARENT, UserRole.TEACHER}) {
            repository.findAllWithPendingPriorityByRole(role, page).forEach(request ->
                assertEquals(role, request.getRequestedRole()));
        }

        boolean reviewedSeen = false;
        var requests = repository.findAllWithPendingPriority(page);
        for (int index = 0; index < requests.getTotalPages(); index++) {
            for (var request : repository.findAllWithPendingPriority(PageRequest.of(index, 1))) {
                if (request.getStatus() == RegistrationStatus.PENDING) {
                    assertFalse(reviewedSeen, "Pending requests must precede reviewed requests across pages");
                } else {
                    reviewedSeen = true;
                }
            }
        }
    }
}
