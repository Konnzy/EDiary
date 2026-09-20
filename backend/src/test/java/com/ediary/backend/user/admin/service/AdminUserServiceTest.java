package com.ediary.backend.user.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.notification.repository.MessageRepository;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.user.admin.dto.CreateAdminUserRequest;
import com.ediary.backend.user.admin.dto.UpdateAdminUserRequest;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.admin.event.AdminUserCreatedEvent;
import com.ediary.backend.user.admin.event.AdminUserPasswordResetEvent;
import com.ediary.backend.user.parent.service.ParentLinkCodeService;
import com.ediary.backend.user.password.repository.PasswordResetTokenRepository;
import com.ediary.backend.user.password.service.TemporaryPasswordGenerator;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.AdministratorProfileRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.session.repository.UserSessionRepository;
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
    @Mock private AdministratorProfileRepository administratorProfileRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private ParentProfileRepository parentProfileRepository;
    @Mock private StudentParentLinkRepository studentParentLinkRepository;
    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private StudentClassMembershipRepository studentClassMembershipRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private StudentHomeworkRepository studentHomeworkRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserNotificationSettingsRepository notificationSettingsRepository;
    @Mock private RegistrationRequestRepository registrationRequestRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TemporaryPasswordGenerator temporaryPasswordGenerator;
    @Mock private ParentLinkCodeService parentLinkCodeService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SessionRegistry sessionRegistry;
    @Mock private SessionInformation sessionInformation;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
            userRepository,
            administratorProfileRepository,
            studentProfileRepository,
            teacherProfileRepository,
            parentProfileRepository,
            studentParentLinkRepository,
            schoolClassRepository,
            scheduleRepository,
            studentClassMembershipRepository,
            subjectRepository,
            gradeRepository,
            attendanceRepository,
            studentHomeworkRepository,
            messageRepository,
            notificationSettingsRepository,
            registrationRequestRepository,
            passwordResetTokenRepository,
            userSessionRepository,
            passwordEncoder,
            temporaryPasswordGenerator,
            parentLinkCodeService,
            eventPublisher,
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
    void rejectsDuplicateLoginWhenEditingAnotherAccount() {
        AppUser user = parentUser();
        user.setEmail("parent@example.com");
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(userRepository.existsByLoginIgnoreCaseAndIdNot("already.used", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(
            10L,
            new UpdateAdminUserRequest(
                "Already.Used",
                "Олена",
                "Іваненко",
                "Петрівна",
                "parent@example.com",
                "+380671234567",
                UserRole.PARENT
            )
        ))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("LOGIN_ALREADY_EXISTS"));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resetsPasswordWithoutReturningTemporaryPassword() {
        AppUser user = parentUser();
        user.setEmail("parent@example.com");
        user.setStatus(com.ediary.backend.user.entity.UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp!Password234");
        when(passwordEncoder.encode("Temp!Password234")).thenReturn("new-password-hash");

        var result = service.resetPassword(10L);

        assertThat(result.message()).doesNotContain("Temp!Password234");
        assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
        assertThat(user.isMustChangePassword()).isTrue();
        verify(passwordResetTokenRepository).markAllUnusedAsUsed(10L);
        verify(userSessionRepository).revokeActiveSessions(
            10L,
            java.time.LocalDateTime.now(CLOCK),
            "ADMIN_PASSWORD_RESET"
        );
        ArgumentCaptor<AdminUserPasswordResetEvent> event = ArgumentCaptor.forClass(
            AdminUserPasswordResetEvent.class
        );
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().temporaryPassword()).isEqualTo("Temp!Password234");
        assertThat(event.getValue().email()).isEqualTo("parent@example.com");
    }

    @Test
    void updatesParentFullNameAndContactDetails() {
        AppUser user = parentUser();
        user.setEmail("parent.old@example.com");
        ParentProfile profile = new ParentProfile();
        profile.setUser(user);
        profile.setName("Олена");
        profile.setSurname("Іваненко");
        profile.setPatronymic("Петрівна");
        profile.setEmail("parent.old@example.com");
        profile.setPhone("+380671111111");
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(parentProfileRepository.findByUser_Id(10L)).thenReturn(Optional.of(profile));

        var result = service.update(
            10L,
            new UpdateAdminUserRequest(
                "parent.new",
                "Марія",
                "Бондаренко",
                "Олексіївна",
                "parent.new@example.com",
                "+380672222222",
                UserRole.PARENT
            )
        );

        assertThat(result.firstName()).isEqualTo("Марія");
        assertThat(result.lastName()).isEqualTo("Бондаренко");
        assertThat(result.patronymic()).isEqualTo("Олексіївна");
        assertThat(profile.getName()).isEqualTo("Марія");
        assertThat(profile.getSurname()).isEqualTo("Бондаренко");
        assertThat(profile.getPatronymic()).isEqualTo("Олексіївна");
        assertThat(profile.getPhone()).isEqualTo("+380672222222");
        verify(parentProfileRepository).save(profile);
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
        when(messageRepository.existsBySender_IdOrReceiver_Id(10L, 10L)).thenReturn(true);

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
        lenient().when(studentClassMembershipRepository.existsByStudent_User_Id(10L)).thenReturn(true);

        var eligibility = service.getDeletionEligibility(10L);

        assertThat(eligibility.deletable()).isFalse();
        assertThat(eligibility.reason()).contains("історію навчання в класі");
    }

    @Test
    void deletesAccountWithoutImportantRelations() {
        AppUser user = parentUser();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        service.delete(10L, "admin");

        verify(registrationRequestRepository).deleteByRequestedStudent_User_Id(10L);
        verify(registrationRequestRepository).deleteByProcessedUser_Id(10L);
        verify(passwordResetTokenRepository).deleteAllByUser_Id(10L);
        verify(userSessionRepository).deleteAllByUser_Id(10L);
        verify(notificationSettingsRepository).deleteByUser_Id(10L);
        verify(parentProfileRepository).deleteByUser_Id(10L);
        verify(userRepository).delete(user);
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
