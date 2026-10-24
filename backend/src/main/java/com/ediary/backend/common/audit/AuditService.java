package com.ediary.backend.common.audit;

import com.ediary.backend.common.logging.DiagnosticContext;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private final AuditEntryRepository entries;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, Object targetId, Map<String, ?> before, Map<String, ?> after) {
        if (before.equals(after)) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        com.ediary.backend.user.entity.AppUser actor = null;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            var session = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                ? servlet.getRequest().getSession(false) : null;
            // A self-service login change must not erase the actor's identity.
            actor = session != null && session.getAttribute("logging.actorId") instanceof Long sessionActor
                ? users.findById(sessionActor).orElse(null)
                : users.findByLoginIgnoreCase(authentication.getName()).orElse(null);
        }
        Long actorId = actor == null ? null : actor.getId();
        String role = actor == null ? "SYSTEM" : actor.getRole().name();
        String id = String.valueOf(targetId);
        if (!action.matches("[A-Z_]{1,80}") || !targetType.matches("[A-Z_]{1,40}") || id.length() > 100) {
            throw new IllegalArgumentException("Invalid audit metadata");
        }
        String oldData = json.writeValueAsString(AuditValues.validate(before, 0));
        String newData = json.writeValueAsString(AuditValues.validate(after, 0));
        if (oldData.length() + newData.length() > 128000) throw new IllegalArgumentException("Audit snapshot is too large");
        entries.save(new AuditEntry(actorId, role, action, targetType, id, MDC.get("requestId"), oldData, newData, clock.instant()));
        Runnable committed = DiagnosticContext.capture(() -> log.info(
            "event=audit.committed action={} actorId={} actorRole={} targetType={} targetId={}", action, actorId, role, targetType, id));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { committed.run(); }
        });
    }
}
