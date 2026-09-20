package com.ediary.backend.user.admin.dashboard;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminActivityRecorder {

    private final AdminActivityRepository repository;
    private final Clock clock;

    @Transactional
    public void record(String actorLogin, AdminActivityAction action) {
        repository.save(new AdminActivity(
            actorLogin, action.title(), action.target(), LocalDateTime.now(clock)
        ));
    }
}
