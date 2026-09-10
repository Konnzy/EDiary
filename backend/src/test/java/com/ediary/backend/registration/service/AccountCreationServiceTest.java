package com.ediary.backend.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.service.TemporaryPasswordGenerator;
import com.ediary.backend.user.service.ParentLinkCodeService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountCreationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private TeacherProfileRepository teacherProfileRepository;
    @Mock
    private ParentProfileRepository parentProfileRepository;
    @Mock
    private StudentParentLinkRepository studentParentLinkRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TemporaryPasswordGenerator temporaryPasswordGenerator;
    @Mock
    private ParentLinkCodeService parentLinkCodeService;

    private AccountCreationService service;

    @BeforeEach
    void setUp() {
        service = new AccountCreationService(
            userRepository,
            studentProfileRepository,
            teacherProfileRepository,
            parentProfileRepository,
            studentParentLinkRepository,
            passwordEncoder,
            temporaryPasswordGenerator,
            parentLinkCodeService,
            Clock.systemUTC()
        );
    }

    @Test
    void rejectsLoginThatAlreadyExists() {
        RegistrationRequest request = org.mockito.Mockito.mock(RegistrationRequest.class);
        when(userRepository.existsByLoginIgnoreCase("student.petrenko")).thenReturn(true);

        assertThatThrownBy(() -> service.createFrom(request, "student.petrenko"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("LOGIN_ALREADY_EXISTS"))
            .hasMessageContaining("логін уже використовується");

        verify(userRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(AppUser.class));
        verify(studentProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(teacherProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(parentProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsParentAndLinksOnlyRequestedStudent() {
        StudentProfile student = new StudentProfile();
        student.setId(17L);
        RegistrationRequest request = new RegistrationRequest(
            "Олена",
            "Коваленко",
            "Іванівна",
            "parent@example.com",
            "+380671234567",
            LocalDate.of(1988, 5, 12),
            UserRole.PARENT,
            null,
            null,
            student,
            LocalDateTime.of(2026, 5, 24, 10, 0)
        );
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp!Password234");
        when(passwordEncoder.encode("Temp!Password234")).thenReturn("password-hash");
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(23L);
            return user;
        });
        when(parentProfileRepository.saveAndFlush(any(ParentProfile.class))).thenAnswer(invocation -> {
            ParentProfile parent = invocation.getArgument(0);
            parent.setId(31L);
            return parent;
        });
        when(studentParentLinkRepository.existsByStudent_Id(17L)).thenReturn(false);

        service.createFrom(request, "parent.kovalenko");

        ArgumentCaptor<StudentParentLink> linkCaptor = ArgumentCaptor.forClass(StudentParentLink.class);
        verify(studentParentLinkRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getStudent()).isSameAs(student);
        assertThat(linkCaptor.getValue().getParent().getId()).isEqualTo(31L);
        assertThat(linkCaptor.getValue().getParent().getPatronymic()).isEqualTo("Іванівна");
        assertThat(linkCaptor.getValue().isPrimaryContact()).isTrue();
    }
}
