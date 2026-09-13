package com.ediary.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.AcademicYear;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.school.repository.SubjectRepository;
import com.ediary.backend.user.dto.request.CreateAdminUserRequest;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.event.AdminUserCreatedEvent;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-05-24T10:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock private UserRepository userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private ParentProfileRepository parentProfileRepository;
    @Mock private StudentParentLinkRepository studentParentLinkRepository;
    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TemporaryPasswordGenerator temporaryPasswordGenerator;
    @Mock private ParentLinkCodeService parentLinkCodeService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SessionRegistry sessionRegistry;
    @Mock private SessionInformation sessionInformation;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
            userRepository,
            studentProfileRepository,
            teacherProfileRepository,
            parentProfileRepository,
            studentParentLinkRepository,
            schoolClassRepository,
            subjectRepository,
            passwordEncoder,
            temporaryPasswordGenerator,
            parentLinkCodeService,
            eventPublisher,
            jdbcTemplate,
            sessionRegistry,
            CLOCK
        );
    }

    @Test
    void createsStudentAndPublishesCredentialsAfterPersistingAccount() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(5L);
        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue("2025/2026");
        schoolClass.setAcademicYear(academicYear);
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp!Password234");
        when(parentLinkCodeService.generateUnique()).thenReturn(
            new ParentLinkCodeService.GeneratedParentLinkCode("A7K9-M2P4", "code-hash")
        );
        when(passwordEncoder.encode("Temp!Password234")).thenReturn("password-hash");
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        var result = service.create(studentRequest(LocalDate.of(2010, 5, 12)));

        assertThat(result.user().login()).isEqualTo("student.new");
        assertThat(result.user().mustChangePassword()).isTrue();
        ArgumentCaptor<StudentProfile> profile = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).saveAndFlush(profile.capture());
        assertThat(profile.getValue().getSchoolClass()).isSameAs(schoolClass);
        assertThat(profile.getValue().getPatronymic()).isEqualTo("Іванівна");
        assertThat(profile.getValue().getUser().getPasswordHash()).isEqualTo("password-hash");
        assertThat(profile.getValue().getParentLinkCodeHash()).isEqualTo("code-hash");

        ArgumentCaptor<AdminUserCreatedEvent> event = ArgumentCaptor.forClass(AdminUserCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().temporaryPassword()).isEqualTo("Temp!Password234");
        assertThat(event.getValue().email()).isEqualTo("student@example.com");
        assertThat(event.getValue().parentLinkCode()).isEqualTo("A7K9-M2P4");
    }

    @Test
    void rejectsDuplicateLoginBeforeCreatingAccount() {
        when(userRepository.existsByLoginIgnoreCase("student.new")).thenReturn(true);

        assertThatThrownBy(() -> service.create(studentRequest(LocalDate.of(2010, 5, 12))))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("LOGIN_ALREADY_EXISTS"));

        verify(userRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsStudentOutsideAllowedAgeRange() {
        assertThatThrownBy(() -> service.create(studentRequest(LocalDate.of(2021, 5, 25))))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("від 5 до 20 років");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDeletedStatusAsAStatusTransition() {
        assertThatThrownBy(() -> service.changeStatus(10L, com.ediary.backend.user.entity.UserStatus.DELETED, "admin"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("INVALID_USER_STATUS_TRANSITION"));
    }

    @Test
    void blockingUserExpiresExistingHttpSessions() {
        AppUser user = parentUser();
        user.setStatus(com.ediary.backend.user.entity.UserStatus.ACTIVE);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(sessionRegistry.getAllSessions("parent.new", false)).thenReturn(List.of(sessionInformation));

        service.changeStatus(10L, com.ediary.backend.user.entity.UserStatus.BLOCKED, "admin");

        verify(sessionInformation).expireNow();
        assertThat(user.getStatus()).isEqualTo(com.ediary.backend.user.entity.UserStatus.BLOCKED);
    }

    @Test
    void preventsDeletionWhenUserHasMessageHistory() {
        AppUser user = parentUser();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(jdbcTemplate.queryForObject(
            contains("FROM message"),
            eq(Boolean.class),
            eq(10L),
            eq(10L)
        )).thenReturn(true);

        var eligibility = service.getDeletionEligibility(10L);

        assertThat(eligibility.deletable()).isFalse();
        assertThat(eligibility.reason()).contains("історію повідомлень");
    }

    @Test
    void preventsDeletionWhenStudentHasHistoricalClassMembership() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setLogin("student.old");
        user.setRole(UserRole.STUDENT);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        lenient().when(jdbcTemplate.queryForObject(
            contains("FROM student_class_membership"),
            eq(Boolean.class),
            eq(10L)
        )).thenReturn(true);

        var eligibility = service.getDeletionEligibility(10L);

        assertThat(eligibility.deletable()).isFalse();
        assertThat(eligibility.reason()).contains("історію навчання в класі");
    }

    @Test
    void deletesAccountWithoutImportantRelations() {
        AppUser user = parentUser();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        service.delete(10L, "admin");

        verify(jdbcTemplate).update(
            "DELETE FROM registration_request WHERE requested_student_id IN (SELECT student_id FROM student WHERE user_id = ?)",
            10L
        );
        verify(jdbcTemplate).update(
            "DELETE FROM registration_request WHERE request_processed_user_id = ?",
            10L
        );
        verify(jdbcTemplate).update("DELETE FROM password_reset_token WHERE user_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM user_session WHERE user_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM user_notification_settings WHERE user_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM parent WHERE user_id = ?", 10L);
        verify(jdbcTemplate).update("DELETE FROM app_user WHERE user_id = ?", 10L);
    }

    private CreateAdminUserRequest studentRequest(LocalDate birthDate) {
        return new CreateAdminUserRequest(
            "Student.New",
            "Олена",
            "Коваленко",
            "Іванівна",
            "Student@Example.com",
            "+380671234567",
            birthDate,
            UserRole.STUDENT,
            5L,
            null,
            null
        );
    }

    private AppUser parentUser() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setLogin("parent.new");
        user.setRole(UserRole.PARENT);
        return user;
    }
}
