package com.ediary.backend.user.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.profile.dto.UpdateNotificationPreferencesRequest;
import com.ediary.backend.user.profile.repository.UserAvatarRepository;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock UserRepository users;
    @Mock StudentProfileRepository students;
    @Mock ParentProfileRepository parents;
    @Mock TeacherProfileRepository teachers;
    @Mock StudentParentLinkRepository links;
    @Mock SchoolClassRepository classes;
    @Mock ScheduleRepository schedules;
    @Mock UserNotificationSettingsRepository settings;
    @Mock UserAvatarRepository avatars;

    ProfileService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        service = new ProfileService(users, students, parents, teachers, links, classes, schedules, settings, avatars, clock, org.mockito.Mockito.mock(com.ediary.backend.common.audit.AuditService.class));
        user = new AppUser();
        user.setId(7L);
        user.setLogin("student.test");
        user.setEmail("student@school.ua");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void studentProfileUsesLinkedParentAndClassTeacher() {
        when(users.findByLoginIgnoreCase("student.test")).thenReturn(Optional.of(user));
        TeacherProfile classTeacher = new TeacherProfile();
        classTeacher.setName("Олександр");
        classTeacher.setSurname("Коваленко");
        classTeacher.setEmail("teacher@school.ua");
        classTeacher.setPhone("+380671234567");
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setNumber(9);
        schoolClass.setLetter("А");
        schoolClass.setClassTeacher(classTeacher);
        StudentProfile student = new StudentProfile();
        student.setId(12L);
        student.setName("Іван");
        student.setSurname("Петренко");
        student.setSchoolClass(schoolClass);
        when(students.findByUser_Id(7L)).thenReturn(Optional.of(student));
        ParentProfile parent = new ParentProfile();
        parent.setEmail("parent@example.com");
        parent.setPhone("+380672222222");
        when(links.findAllByStudent_IdOrderByPrimaryContactDesc(12L))
            .thenReturn(List.of(new StudentParentLink(student, parent, true)));

        var response = service.find("student.test");

        assertThat(response.education().className()).isEqualTo("9-А");
        assertThat(response.education().teacherName()).isEqualTo("Коваленко Олександр");
        assertThat(response.education().parentEmail()).isEqualTo("parent@example.com");
    }

    @Test
    void studentCannotUpdatePhone() {
        when(users.findByLoginIgnoreCase("student.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updatePhone("student.test", "+380671234567"))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("учня");
    }

    @Test
    void teacherProfileIncludesOnlyCurrentYearClasses() {
        user.setRole(UserRole.TEACHER);
        when(users.findByLoginIgnoreCase("student.test")).thenReturn(Optional.of(user));
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(25L);
        teacher.setName("Олена");
        teacher.setSurname("Ткаченко");
        teacher.setPhone("+380671234567");
        Subject subject = new Subject();
        subject.setTitle("Математика");
        teacher.getSubjects().add(subject);
        when(teachers.findByUser_Id(7L)).thenReturn(Optional.of(teacher));
        var assignment = mock(ScheduleRepository.TeacherClassAssignment.class);
        when(assignment.getClassNumber()).thenReturn(9);
        when(assignment.getClassLetter()).thenReturn("А");
        when(schedules.findActiveTeacherClassAssignmentsForYear(25L, "2026/2027"))
            .thenReturn(List.of(assignment));
        SchoolClass currentClass = new SchoolClass();
        currentClass.setNumber(9);
        currentClass.setLetter("А");
        AcademicYear currentYear = new AcademicYear();
        currentYear.setValue("2026/2027");
        currentClass.setAcademicYear(currentYear);
        SchoolClass oldClass = new SchoolClass();
        oldClass.setNumber(8);
        oldClass.setLetter("А");
        AcademicYear oldYear = new AcademicYear();
        oldYear.setValue("2025/2026");
        oldClass.setAcademicYear(oldYear);
        when(classes.findAllByClassTeacherIdOrderByNumberAscLetterAsc(25L))
            .thenReturn(List.of(oldClass, currentClass));

        var response = service.find("student.test");

        assertThat(response.professional().subjects()).containsExactly("Математика");
        assertThat(response.professional().classes()).containsExactly("9-А");
        assertThat(response.professional().homeroomClasses()).containsExactly("9-А");
    }

    @Test
    void rejectsNonImageEvenWhenContentTypeClaimsPng() {
        when(users.findByLoginIgnoreCase("student.test")).thenReturn(Optional.of(user));
        var file = new MockMultipartFile("file", "avatar.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> service.uploadAvatar("student.test", file))
            .isInstanceOf(ApplicationException.class)
            .hasMessageContaining("JPG або PNG");
    }

    @Test
    void requiresAllNotificationOptionsForRole() {
        when(users.findByLoginIgnoreCase("student.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateNotifications("student.test",
            new UpdateNotificationPreferencesRequest(true, null, true, null, null, null, null, null, null)))
            .isInstanceOf(ApplicationException.class);
    }
}
