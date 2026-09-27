package com.ediary.backend.schedule.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "app.bootstrap-admin.enabled=false"})
@EnabledIfEnvironmentVariable(named = "EDIARY_RUN_DB_TESTS", matches = "true")
@Transactional(readOnly = true)
class LessonRepositoryIntegrationTest {
    @Autowired
    private LessonRepository repository;

    @Test
    void journalRecordCheckExecutesOnPostgres() {
        assertFalse(repository.hasJournalRecords(-1L));
        repository.findAll().stream().findFirst().ifPresent(lesson -> repository.hasJournalRecords(lesson.getId()));
    }
}
