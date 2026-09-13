package com.ediary.backend.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.dto.request.SaveScheduleLessonRequest;
import com.ediary.backend.school.dto.request.SaveScheduleRequest;
import com.ediary.backend.school.entity.ScheduleEntry;
import com.ediary.backend.school.entity.AcademicYear;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
            academicYearRepository,
            Clock.fixed(Instant.parse("2026-09-12T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsAcademicYearThatHasNotStarted() {
        assertThatThrownBy(() -> service.createAcademicYear("2027/2028"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("ACADEMIC_YEAR_NOT_STARTED"));
    }

    @Test
    void savesLessonUsingFixedBellTimes() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        schoolClass.setAcademicYear(academicYear("2026/2027"));
        Subject subject = new Subject();
        subject.setId(2L);
        subject.setTitle("Математика");
        TeacherProfile teacher = activeTeacher(3L, subject);

        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(schoolClassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findAllById(Set.of(2L))).thenReturn(List.of(subject));
        when(teacherProfileRepository.findAllForSchedule(Set.of(3L))).thenReturn(List.of(teacher));
        when(scheduleRepository.findTeacherConflicts(1L, "2026/2027", 1)).thenReturn(List.of());
        when(scheduleRepository.findActiveForUpdate(
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
        schoolClass.setAcademicYear(academicYear("2026/2027"));
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

    @Test
    void archivesReferencedSlotBeforeReplacingIt() {
        Subject subject = subject(2L);
        TeacherProfile teacher = activeTeacher(3L, subject);
        ScheduleEntry recorded = existingEntry(subject, teacher);
        prepareSave(recorded, subject, teacher);
        when(scheduleRepository.findIdsReferencedByLessons(List.of(11L))).thenReturn(List.of(11L));
        when(scheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(1L, "2026/2027", 1, new SaveScheduleRequest(List.of(
            new SaveScheduleLessonRequest(Weekday.MONDAY, 1, 2L, 3L, "202")
        )));

        assertThat(recorded.isActive()).isFalse();
        assertThat(recorded.getArchivedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 9, 0));
        assertThat(recorded.getRoom()).isEqualTo("101");
        assertThat(response.lessons()).singleElement()
            .satisfies(lesson -> assertThat(lesson.room()).isEqualTo("202"));
        ArgumentCaptor<List<ScheduleEntry>> saved = scheduleEntriesCaptor();
        verify(scheduleRepository, times(2)).saveAllAndFlush(saved.capture());
        assertThat(saved.getAllValues().get(0)).containsExactly(recorded);
        assertThat(saved.getAllValues().get(1)).singleElement()
            .satisfies(entry -> {
                assertThat(entry).isNotSameAs(recorded);
                assertThat(entry.isActive()).isTrue();
            });
        verify(scheduleRepository, never()).deleteAll(anyList());
    }

    @Test
    void keepsReferencedSlotWhenDefinitionIsUnchanged() {
        Subject subject = subject(2L);
        TeacherProfile teacher = activeTeacher(3L, subject);
        ScheduleEntry recorded = existingEntry(subject, teacher);
        prepareSave(recorded, subject, teacher);
        when(scheduleRepository.findIdsReferencedByLessons(List.of(11L))).thenReturn(List.of(11L));
        when(scheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(1L, "2026/2027", 1, new SaveScheduleRequest(List.of(
            new SaveScheduleLessonRequest(Weekday.MONDAY, 1, 2L, 3L, "101")
        )));

        assertThat(recorded.isActive()).isTrue();
        assertThat(recorded.getArchivedAt()).isNull();
        assertThat(response.lessons()).singleElement()
            .satisfies(lesson -> assertThat(lesson.id()).isEqualTo(11L));
        verify(scheduleRepository, times(1)).saveAllAndFlush(anyList());
        verify(scheduleRepository, never()).deleteAll(anyList());
    }

    @Test
    void archivesReferencedSlotWhenRemoved() {
        Subject subject = subject(2L);
        TeacherProfile teacher = activeTeacher(3L, subject);
        ScheduleEntry recorded = existingEntry(subject, teacher);
        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(schoolClassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(recorded.getSchoolClass()));
        when(scheduleRepository.findActiveForUpdate(1L, "2026/2027", 1)).thenReturn(List.of(recorded));
        when(scheduleRepository.findIdsReferencedByLessons(List.of(11L))).thenReturn(List.of(11L));
        when(scheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(1L, "2026/2027", 1, new SaveScheduleRequest(List.of()));

        assertThat(response.lessons()).isEmpty();
        assertThat(recorded.isActive()).isFalse();
        verify(scheduleRepository, never()).deleteAll(anyList());
    }

    private void prepareSave(ScheduleEntry existing, Subject subject, TeacherProfile teacher) {
        when(academicYearRepository.existsById("2026/2027")).thenReturn(true);
        when(schoolClassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing.getSchoolClass()));
        when(subjectRepository.findAllById(Set.of(2L))).thenReturn(List.of(subject));
        when(teacherProfileRepository.findAllForSchedule(Set.of(3L))).thenReturn(List.of(teacher));
        when(scheduleRepository.findActiveForUpdate(1L, "2026/2027", 1)).thenReturn(List.of(existing));
    }

    private ScheduleEntry existingEntry(Subject subject, TeacherProfile teacher) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        schoolClass.setAcademicYear(academicYear("2026/2027"));
        ScheduleEntry entry = new ScheduleEntry();
        entry.setId(11L);
        entry.setSchoolClass(schoolClass);
        entry.setSubject(subject);
        entry.setTeacher(teacher);
        entry.setAcademicYear("2026/2027");
        entry.setWeekNumber(1);
        entry.setWeekday(Weekday.MONDAY);
        entry.setLessonNumber(1);
        entry.setRoom("101");
        entry.setStartTime(LocalTime.of(8, 0));
        entry.setEndTime(LocalTime.of(8, 45));
        return entry;
    }

    private Subject subject(Long id) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setTitle("Математика");
        return subject;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<ScheduleEntry>> scheduleEntriesCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
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

    private AcademicYear academicYear(String value) {
        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue(value);
        return academicYear;
    }
}
