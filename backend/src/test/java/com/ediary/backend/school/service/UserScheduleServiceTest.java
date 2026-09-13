package com.ediary.backend.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.entity.AcademicYear;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.repository.AcademicYearRepository;
import com.ediary.backend.school.repository.ScheduleRepository;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
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

@ExtendWith(MockitoExtension.class)
class UserScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private AcademicYearRepository academicYearRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private StudentParentLinkRepository studentParentLinkRepository;

    private UserScheduleService service;

    @BeforeEach
    void setUp() {
        service = new UserScheduleService(
            scheduleRepository,
            academicYearRepository,
            studentProfileRepository,
            studentParentLinkRepository,
            Clock.fixed(Instant.parse("2026-09-12T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void returnsOnlyAuthenticatedStudentsClassSchedule() {
        SchoolClass schoolClass = schoolClass(9L, 9, "А");
        StudentProfile student = new StudentProfile();
        student.setSchoolClass(schoolClass);
        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(studentProfileRepository.findByUser_LoginIgnoreCase("student.one"))
            .thenReturn(Optional.of(student));
        when(scheduleRepository
            .findAllBySchoolClass_IdAndAcademicYearAndWeekNumberOrderByWeekdayAscLessonNumberAsc(
                9L, "2026/2027", 1
            ))
            .thenReturn(List.of());

        var response = service.findForStudent("student.one", 1);

        assertThat(response.className()).isEqualTo("9-А");
        assertThat(response.lessons()).isEmpty();
        verify(scheduleRepository)
            .findAllBySchoolClass_IdAndAcademicYearAndWeekNumberOrderByWeekdayAscLessonNumberAsc(
                9L, "2026/2027", 1
            );
    }

    @Test
    void returnsEmptyScheduleWhenStudentHasNoClass() {
        StudentProfile student = new StudentProfile();
        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(studentProfileRepository.findByUser_LoginIgnoreCase("student.one"))
            .thenReturn(Optional.of(student));

        var response = service.findForStudent("student.one", 2);

        assertThat(response.className()).isNull();
        assertThat(response.lessons()).isEmpty();
    }

    @Test
    void rejectsChildThatIsNotLinkedToAuthenticatedParent() {
        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(studentParentLinkRepository.findByParentLoginAndStudentId("parent.one", 22L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.findForParent("parent.one", 22L, 1)
        )
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("PARENT_CHILD_NOT_FOUND"));
    }

    @Test
    void readsTeacherScheduleByAuthenticatedLogin() {
        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(scheduleRepository.findAllForTeacher("teacher.one", "2026/2027", 2))
            .thenReturn(List.of());

        var response = service.findForTeacher("teacher.one", 2);

        assertThat(response.week()).isEqualTo(2);
        verify(scheduleRepository).findAllForTeacher("teacher.one", "2026/2027", 2);
    }

    @Test
    void returnsCurrentAcademicPeriodInOptions() {
        AcademicYear year = new AcademicYear();
        year.setValue("2026/2027");
        when(academicYearRepository.findAllByOrderByValueAsc()).thenReturn(List.of(year));

        var options = service.findOptions();

        assertThat(options.academicYears()).containsExactly("2026/2027");
        assertThat(options.currentAcademicYear()).isEqualTo("2026/2027");
        assertThat(options.currentWeek()).isEqualTo(2);
    }

    private SchoolClass schoolClass(Long id, int number, String letter) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setNumber(number);
        schoolClass.setLetter(letter);
        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue("2026/2027");
        schoolClass.setAcademicYear(academicYear);
        return schoolClass;
    }
}
