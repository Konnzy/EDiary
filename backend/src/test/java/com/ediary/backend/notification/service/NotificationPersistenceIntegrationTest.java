package com.ediary.backend.notification.service;

import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.notification.repository.UserNotificationRepository;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.notification.entity.UserNotificationSettings;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.mail.enabled=false", "app.bootstrap-admin.enabled=false", "app.notifications.scheduling-enabled=false"})
@EnabledIfEnvironmentVariable(named = "EDIARY_RUN_DB_TESTS", matches = "true")
class NotificationPersistenceIntegrationTest {
    private static final String SCHEMA = "notification_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv().getOrDefault("EDIARY_DB_URL", "jdbc:postgresql://localhost:5432/ediary");
    private static final String USER = System.getenv().getOrDefault("EDIARY_DB_USERNAME", "ediary");
    private static final String PASSWORD = System.getenv().getOrDefault("EDIARY_DB_PASSWORD", "ediary");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (!SCHEMA.matches("notification_test_[a-f0-9]{32}")) throw new IllegalStateException("Invalid test schema");
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserNotificationRepository notifications;
    @Autowired UserNotificationSettingsRepository preferences;
    @Autowired UserRepository users;
    @Autowired com.ediary.backend.common.audit.AuditService audit;
    @Autowired com.ediary.backend.common.audit.AuditEntryRepository auditEntries;
    @Autowired StudentProfileRepository students;
    @Autowired LessonRepository lessons;
    @Autowired UserNotificationService service;
    @Autowired NotificationEmailSender sender;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean EmailService email;
    @MockitoBean Clock clock;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @Autowired com.ediary.backend.common.logging.RequestLogFilter requestLogFilter;

    @BeforeEach
    void defaultClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void requestIdIsReturnedForSecurityErrorsAndAllowedThroughCors() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
            .addFilters(requestLogFilter)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String requestId = UUID.randomUUID().toString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/users")
                .header("Origin", "http://127.0.0.1:5173").header("X-Request-ID", requestId))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Request-ID", requestId))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Expose-Headers", "X-Request-ID"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.requestId").value(requestId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"password\":\"secret-password\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void auditCommitsWithBusinessTransactionAndRollsBackAtomically() {
        Long userId = id("insert into app_user(user_login,user_email,user_password_hash,user_role) values ('audit.teacher','audit@example.invalid','test','TEACHER') returning user_id");
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("audit.teacher", null,
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TEACHER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        String requestId = UUID.randomUUID().toString();
        org.slf4j.MDC.put("requestId", requestId);
        var tx = new TransactionTemplate(transactions);
        try {
            long initial = auditEntries.count();
            tx.executeWithoutResult(status -> {
                audit.record("JOURNAL_CELL_UPDATED", "JOURNAL_CELL", "test:1", com.ediary.backend.common.audit.AuditValues.of("score", 8), com.ediary.backend.common.audit.AuditValues.of("score", 10));
                auditEntries.flush();
                status.setRollbackOnly();
            });
            assertThat(auditEntries.count()).isEqualTo(initial);
            tx.executeWithoutResult(status -> audit.record("JOURNAL_CELL_UPDATED", "JOURNAL_CELL", "test:1",
                com.ediary.backend.common.audit.AuditValues.of("score", 8), com.ediary.backend.common.audit.AuditValues.of("score", 10)));
            assertThat(auditEntries.count()).isEqualTo(initial + 1);
            var entry = auditEntries.findAll().stream().filter(value -> value.getTargetId().equals("test:1")).findFirst().orElseThrow();
            assertThat(entry.getActorUserId()).isEqualTo(userId);
            assertThat(entry.getRequestId()).isEqualTo(requestId);
            assertThat(entry.getBeforeData()).contains("8");
            assertThat(entry.getAfterData()).contains("10").doesNotContain("password", "email");
            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.getSession().setAttribute("logging.actorId", userId);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
            tx.executeWithoutResult(status -> {
                jdbc.update("update app_user set user_login='audit.teacher.changed' where user_id=?", userId);
                audit.record("USER_UPDATED", "USER", userId, java.util.Map.of(), com.ediary.backend.common.audit.AuditValues.of("loginChanged", true));
            });
            assertThat(auditEntries.findAll().stream().filter(value -> value.getAction().equals("USER_UPDATED")).findFirst().orElseThrow().getActorUserId()).isEqualTo(userId);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            org.slf4j.MDC.clear();
        }
    }

    @Test
    void migrationQueueOwnershipAndCorrectionSurviveTransactionReloads() {
        var now = new AtomicReference<>(Instant.parse("2026-10-24T10:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(email.isEnabled()).thenReturn(true);
        when(email.sendText(anyString(), anyString(), anyString())).thenReturn(true);
        Long user = id("insert into app_user(user_login,user_email,user_password_hash,user_role) values ('notification.student','student@example.invalid','test','STUDENT') returning user_id");
        Long teacherUser = id("insert into app_user(user_login,user_email,user_password_hash,user_role) values ('notification.teacher','teacher@example.invalid','test','TEACHER') returning user_id");
        Long teacher = id("insert into teacher(user_id,teacher_name,teacher_surname,teacher_birthdate,teacher_email,teacher_phone) values (?, 'Test','Teacher','1980-01-01','teacher@example.invalid','000') returning teacher_id", teacherUser);
        Long schoolClass = id("insert into school_class(class_number,class_letter,class_academic_year) values (9,'А','2026/2027') returning class_id");
        Long student = id("insert into student(user_id,class_id,student_name,student_surname,student_birthdate,student_enrollment_date,student_status) values (?,?,'Test','Student','2010-01-01','2026-09-01','STUDYING') returning student_id", user, schoolClass);
        Long subject = id("insert into subject(subject_title) values ('Notification test') returning subject_id");
        jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)", teacher, subject);
        Long schedule = id("insert into schedule(class_id,subject_id,teacher_id,schedule_weekday,schedule_lesson_number,schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from) values (?,?,?,'MONDAY',1,'08:00','08:45','2026/2027',1,'2026-09-01') returning schedule_id", schoolClass, subject, teacher);
        Long lesson = id("insert into lesson(schedule_id,lesson_date,lesson_topic) values (?,'2026-10-24','Test') returning lesson_id", schedule);
        jdbc.update("insert into attendance(student_id,lesson_id,attendance_status) values (?,?,'ABSENT')", student, lesson);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> service.attendanceChanged(students.findById(student).orElseThrow(), lessons.findById(lesson).orElseThrow(), null, AttendanceStatus.ABSENT));
        Long notification = jdbc.queryForObject("select notification_id from user_notification where recipient_id=?", Long.class, user);
        // A separate transaction reads the persisted queue, as a restarted worker would.
        tx.executeWithoutResult(status -> {
            var stored = notifications.findForDelivery(notification).orElseThrow();
            assertThat(stored.getEmailDueAt()).isEqualTo(LocalDateTime.of(2026, 10, 24, 10, 1));
            assertThat(stored.getEventKey()).isEqualTo("attendance:" + student + ":" + lesson);
            assertThat(notifications.findDueIds(LocalDateTime.of(2026, 10, 24, 10, 0), org.springframework.data.domain.PageRequest.of(0, 50))).isEmpty();
        });
        now.set(now.get().plusSeconds(61));
        sender.deliver(notification);
        sender.deliver(notification);
        verify(email, times(1)).sendText(anyString(), anyString(), anyString());
        service.dismiss("notification.student", notification);
        assertThat(service.feed("notification.student").items()).isEmpty();
        assertThatThrownBy(() -> service.dismiss("notification.teacher", notification)).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        jdbc.update("update attendance set attendance_status='PRESENT' where lesson_id=?", lesson);
        tx.executeWithoutResult(status -> service.attendanceChanged(students.findById(student).orElseThrow(), lessons.findById(lesson).orElseThrow(), AttendanceStatus.ABSENT, AttendanceStatus.PRESENT));
        assertThat(service.feed("notification.student").unreadCount()).isEqualTo(1);
        sender.deliver(notification);
        verify(email, times(2)).sendText(anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("select count(*) from user_notification", Integer.class)).isEqualTo(1);
        tx.executeWithoutResult(status -> {
            var setting = new UserNotificationSettings();
            setting.setUser(users.findById(user).orElseThrow());
            setting.setAttendanceEmailEnabled(false);
            setting.getInApp().put("absence", true);
            preferences.saveAndFlush(setting);
        });
        tx.executeWithoutResult(status -> {
            var setting = preferences.findByUser_Id(user).orElseThrow();
            assertThat(setting.isAttendanceEmailEnabled()).isFalse();
            assertThat(setting.getInApp()).containsEntry("absence", true);
        });
    }

    private Long id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }
}
