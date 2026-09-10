package com.ediary.backend.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.dto.request.SaveScheduleLessonRequest;
import com.ediary.backend.school.dto.request.SaveScheduleRequest;
import com.ediary.backend.school.entity.ScheduleEntry;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.Subject;
import com.ediary.backend.school.entity.Weekday;
import com.ediary.backend.school.repository.AcademicYearRepository;
import com.ediary.backend.school.repository.ScheduleRepository;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.school.repository.SubjectRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminScheduleServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private AcademicYearRepository academicYearRepository;

    private AdminScheduleService service;

    @BeforeEach
    void setUp() {
        service = new AdminScheduleService(
            scheduleRepository,
            schoolClassRepository,
            subjectRepository,
            teacherProfileRepository,
            academicYearRepository
        );
    }

    @Test
    void savesLessonUsingFixedBellTimes() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        Subject subject = new Subject();
        subject.setId(2L);
        subject.setTitle("Математика");
        TeacherProfile teacher = activeTeacher(3L, subject);

        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(schoolClassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findAllById(Set.of(2L))).thenReturn(List.of(subject));
        when(teacherProfileRepository.findAllForSchedule(Set.of(3L))).thenReturn(List.of(teacher));
        when(scheduleRepository.findTeacherConflicts(1L, "2026/2027", 1)).thenReturn(List.of());
        when(scheduleRepository.findAllBySchoolClass_IdAndAcademicYearAndWeekNumberOrderByWeekdayAscLessonNumberAsc(
            1L, "2026/2027", 1
        )).thenReturn(List.of());
        when(scheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<ScheduleEntry> entries = invocation.getArgument(0);
            entries.getFirst().setId(10L);
            return entries;
        });

        var response = service.save(
            1L,
            "2026/2027",
            1,
            new SaveScheduleRequest(List.of(
                new SaveScheduleLessonRequest(Weekday.MONDAY, 2, 2L, 3L, "302")
            ))
        );

        assertThat(response.lessons()).hasSize(1);
        assertThat(response.lessons().getFirst().startTime()).isEqualTo("08:55");
        assertThat(response.lessons().getFirst().endTime()).isEqualTo("09:40");
        assertThat(response.lessons().getFirst().subject()).isEqualTo("Математика");
    }

    @Test
    void rejectsTeacherOccupiedInAnotherClass() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        Subject subject = new Subject();
        subject.setId(2L);
        TeacherProfile teacher = activeTeacher(3L, subject);
        ScheduleEntry occupied = new ScheduleEntry();
        occupied.setTeacher(teacher);
        occupied.setWeekday(Weekday.MONDAY);
        occupied.setLessonNumber(1);

        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(schoolClassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findAllById(Set.of(2L))).thenReturn(List.of(subject));
        when(teacherProfileRepository.findAllForSchedule(Set.of(3L))).thenReturn(List.of(teacher));
        when(scheduleRepository.findTeacherConflicts(1L, "2026/2027", 1)).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> service.save(
            1L,
            "2026/2027",
            1,
            new SaveScheduleRequest(List.of(
                new SaveScheduleLessonRequest(Weekday.MONDAY, 1, 2L, 3L, "302")
            ))
        ))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("TEACHER_SCHEDULE_CONFLICT"));
    }

    private TeacherProfile activeTeacher(Long id, Subject subject) {
        AppUser user = new AppUser();
        user.setStatus(UserStatus.ACTIVE);
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(id);
        teacher.setName("Олена");
        teacher.setSurname("Коваленко");
        teacher.setUser(user);
        teacher.getSubjects().add(subject);
        return teacher;
    }
}
