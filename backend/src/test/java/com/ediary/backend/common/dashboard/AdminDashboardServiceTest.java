package com.ediary.backend.common.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.school.dto.response.ScheduleOptionsResponse;
import com.ediary.backend.school.entity.ScheduleEntry;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.Subject;
import com.ediary.backend.school.entity.Weekday;
import com.ediary.backend.school.repository.ScheduleRepository;
import com.ediary.backend.school.service.UserScheduleService;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RegistrationRequestRepository registrationRequestRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private UserScheduleService userScheduleService;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
            userRepository,
            registrationRequestRepository,
            scheduleRepository,
            userScheduleService,
            Clock.fixed(Instant.parse("2026-09-14T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void returnsCurrentCountsRequestsAndLessons() {
        when(userRepository.countByRoleAndStatusNot(UserRole.STUDENT, UserStatus.DELETED)).thenReturn(10L);
        when(userRepository.countByRoleAndStatus(UserRole.STUDENT, UserStatus.ACTIVE)).thenReturn(9L);
        when(userRepository.countByRoleAndStatusNot(UserRole.TEACHER, UserStatus.DELETED)).thenReturn(20L);
        when(userRepository.countByRoleAndStatus(UserRole.TEACHER, UserStatus.ACTIVE)).thenReturn(18L);
        when(userRepository.countByRoleAndStatusNot(UserRole.PARENT, UserStatus.DELETED)).thenReturn(5L);
        when(userRepository.countByRoleAndStatus(UserRole.PARENT, UserStatus.ACTIVE)).thenReturn(5L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(33L);
        when(userRepository.countByStatusNot(UserStatus.DELETED)).thenReturn(36L);
        when(registrationRequestRepository.countByStatus(RegistrationStatus.PENDING)).thenReturn(2L);
        when(registrationRequestRepository.findTop4ByOrderByCreatedAtDesc()).thenReturn(List.of(
            new RegistrationRequest(
                "Олена", "Мельник", null, "olena@example.com", "+380000000000",
                LocalDate.of(1990, 1, 1), UserRole.PARENT, null, null, null,
                java.time.LocalDateTime.of(2026, 9, 14, 8, 30)
            )
        ));
        when(userScheduleService.findOptions()).thenReturn(
            new ScheduleOptionsResponse(List.of("2026/2027"), "2026/2027", 1)
        );
        when(scheduleRepository
            .findAllByAcademicYearAndWeekNumberAndWeekdayOrderByLessonNumberAscSchoolClass_NumberAscSchoolClass_LetterAsc(
                "2026/2027", 1, Weekday.MONDAY
            )).thenReturn(List.of(lesson()));

        var result = service.find();

        assertThat(result.stats().students()).isEqualTo(10L);
        assertThat(result.stats().pendingRequests()).isEqualTo(2L);
        assertThat(result.recentRequests()).singleElement()
            .satisfies(request -> assertThat(request.fullName()).isEqualTo("Мельник Олена"));
        assertThat(result.todayLessons()).singleElement()
            .satisfies(lesson -> {
                assertThat(lesson.subject()).isEqualTo("Математика");
                assertThat(lesson.className()).isEqualTo("9-А");
                assertThat(lesson.startTime()).isEqualTo("08:00");
            });
    }

    private ScheduleEntry lesson() {
        Subject subject = new Subject();
        subject.setTitle("Математика");
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setNumber(9);
        schoolClass.setLetter("А");
        TeacherProfile teacher = new TeacherProfile();
        teacher.setSurname("Коваленко");
        teacher.setName("Олександр");

        ScheduleEntry lesson = new ScheduleEntry();
        lesson.setId(1L);
        lesson.setSubject(subject);
        lesson.setSchoolClass(schoolClass);
        lesson.setTeacher(teacher);
        lesson.setRoom("120");
        lesson.setStartTime(LocalTime.of(8, 0));
        return lesson;
    }
}
