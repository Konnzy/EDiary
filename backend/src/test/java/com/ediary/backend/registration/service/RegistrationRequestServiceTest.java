package com.ediary.backend.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.registration.dto.request.CreateRegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.event.RegistrationApprovedEvent;
import com.ediary.backend.registration.mapper.RegistrationRequestMapper;
import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.school.repository.SubjectRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.service.ParentLinkCodeService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RegistrationRequestServiceTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-05-24T10:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private RegistrationRequestRepository registrationRequestRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ParentLinkCodeService parentLinkCodeService;
    @Mock
    private AccountCreationService accountCreationService;
    @Mock
    private RegistrationRequestMapper mapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RegistrationRequestService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationRequestService(
            registrationRequestRepository,
            schoolClassRepository,
            subjectRepository,
            userRepository,
            parentLinkCodeService,
            accountCreationService,
            mapper,
            eventPublisher,
            CLOCK
        );
    }

    @Test
    void submitsStudentRequestWithNormalizedContactData() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(5L);
        schoolClass.setNumber(9);
        schoolClass.setLetter("Б");
        schoolClass.setLimit(30);

        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(registrationRequestRepository.existsByEmailIgnoreCaseAndStatus(
            "student@example.com",
            RegistrationStatus.PENDING
        )).thenReturn(false);
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(registrationRequestRepository.saveAndFlush(any(RegistrationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(new CreateRegistrationRequest(
            "  Даніл ",
            " Холоденко ",
            " Романович ",
            " Student@Example.com ",
            "67 123 45 67",
            LocalDate.of(2010, 5, 12),
            UserRole.STUDENT,
            5L,
            null,
            null,
            true
        ));

        ArgumentCaptor<RegistrationRequest> requestCaptor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(registrationRequestRepository).saveAndFlush(requestCaptor.capture());
        RegistrationRequest saved = requestCaptor.getValue();

        assertThat(response.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(saved.getEmail()).isEqualTo("student@example.com");
        assertThat(saved.getPatronymic()).isEqualTo("Романович");
        assertThat(saved.getPhone()).isEqualTo("+380671234567");
        assertThat(saved.getRequestedClass()).isSameAs(schoolClass);
        assertThat(saved.getTermsAcceptedAt()).isEqualTo(LocalDateTime.of(2026, 5, 24, 10, 0));
    }

    @Test
    void submitsParentRequestForStudentResolvedByLinkCode() {
        StudentProfile student = new StudentProfile();
        student.setId(12L);
        when(userRepository.existsByEmailIgnoreCase("adult@example.com")).thenReturn(false);
        when(registrationRequestRepository.existsByEmailIgnoreCaseAndStatus(
            "adult@example.com",
            RegistrationStatus.PENDING
        )).thenReturn(false);
        when(parentLinkCodeService.requireActiveStudent("A7K9-M2P4")).thenReturn(student);
        when(registrationRequestRepository.saveAndFlush(any(RegistrationRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(adultRequest(UserRole.PARENT, LocalDate.of(1988, 5, 12)));

        ArgumentCaptor<RegistrationRequest> requestCaptor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(registrationRequestRepository).saveAndFlush(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestedStudent()).isSameAs(student);
        assertThat(requestCaptor.getValue().getRequestedClass()).isNull();
        verify(parentLinkCodeService).requireActiveStudent("A7K9-M2P4");
        verify(schoolClassRepository, never()).findById(any());
    }

    @Test
    void rejectsDuplicatePendingRequestWithoutSavingPersonalData() {
        when(userRepository.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(registrationRequestRepository.existsByEmailIgnoreCaseAndStatus(
            "student@example.com",
            RegistrationStatus.PENDING
        )).thenReturn(true);

        assertThatThrownBy(() -> service.submit(studentRequest()))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("вже перебуває на розгляді");

        verify(registrationRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStudentYoungerThanFive() {
        assertThatThrownBy(() -> service.submit(studentRequest(LocalDate.of(2021, 5, 25))))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("від 5 до 20 років");

        verify(registrationRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStudentOlderThanTwenty() {
        assertThatThrownBy(() -> service.submit(studentRequest(LocalDate.of(2005, 5, 23))))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("від 5 до 20 років");

        verify(registrationRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsParentYoungerThanEighteen() {
        assertThatThrownBy(() -> service.submit(adultRequest(UserRole.PARENT, LocalDate.of(2008, 5, 25))))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("від 18 до 100 років");

        verify(registrationRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsTeacherOlderThanOneHundred() {
        assertThatThrownBy(() -> service.submit(adultRequest(UserRole.TEACHER, LocalDate.of(1925, 5, 23))))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("від 18 до 100 років");

        verify(registrationRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvalCreatesAccountAndPublishesCredentialsOnlyAfterDecisionIsStored() {
        SchoolClass schoolClass = new SchoolClass();
        RegistrationRequest request = new RegistrationRequest(
            "Даніл",
            "Холоденко",
            null,
            "student@example.com",
            "+380671234567",
            LocalDate.of(2010, 5, 12),
            UserRole.STUDENT,
            schoolClass,
            null,
            null,
            LocalDateTime.of(2026, 5, 23, 9, 0)
        );
        AppUser reviewer = new AppUser();
        reviewer.setLogin("admin");
        reviewer.setRole(UserRole.ADMIN);
        reviewer.setStatus(UserStatus.ACTIVE);
        AppUser createdUser = new AppUser();
        createdUser.setLogin("st23456789");

        when(registrationRequestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(userRepository.findByLoginIgnoreCase("admin")).thenReturn(Optional.of(reviewer));
        when(accountCreationService.createFrom(request, "student.petrenko"))
            .thenReturn(new AccountProvisioningResult(createdUser, "Temp!Password234", "A7K9-M2P4"));
        when(registrationRequestRepository.saveAndFlush(request)).thenReturn(request);

        service.approve(7L, "admin", " Student.Petrenko ");

        assertThat(request.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(request.getProcessedUser()).isSameAs(createdUser);
        verify(registrationRequestRepository).saveAndFlush(request);
        verify(accountCreationService).createFrom(request, "student.petrenko");
        verify(eventPublisher).publishEvent(any(RegistrationApprovedEvent.class));
    }

    private CreateRegistrationRequest studentRequest() {
        return studentRequest(LocalDate.of(2010, 5, 12));
    }

    private CreateRegistrationRequest studentRequest(LocalDate birthDate) {
        return new CreateRegistrationRequest(
            "Даніл",
            "Холоденко",
            null,
            "student@example.com",
            "+380671234567",
            birthDate,
            UserRole.STUDENT,
            5L,
            null,
            null,
            true
        );
    }

    private CreateRegistrationRequest adultRequest(UserRole role, LocalDate birthDate) {
        return new CreateRegistrationRequest(
            "Олена",
            "Коваленко",
            null,
            "adult@example.com",
            "+380671234567",
            birthDate,
            role,
            null,
            role == UserRole.TEACHER ? 3L : null,
            role == UserRole.PARENT ? "A7K9-M2P4" : null,
            true
        );
    }
}
