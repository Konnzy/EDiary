package com.ediary.backend.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.dto.SaveJournalCellRequest;
import com.ediary.backend.journal.dto.SaveJournalLessonRequest;
import com.ediary.backend.journal.entity.Attendance;
import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.entity.GradeType;
import com.ediary.backend.journal.entity.LessonWorkType;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.journal.repository.HomeworkRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.dto.response.LessonResponse;
import com.ediary.backend.schedule.repository.AcademicYearRepository;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.schedule.service.LessonService;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private StudentClassMembershipRepository membershipRepository;
    @Mock private AcademicYearRepository academicYearRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private HomeworkRepository homeworkRepository;
    @Mock private StudentHomeworkRepository studentHomeworkRepository;
    @Mock private GradeService gradeService;
    @Mock private AttendanceService attendanceService;
    @Mock private LessonService lessonService;
    @Mock private Clock clock;
    @InjectMocks private JournalService service;

    private static final SaveJournalCellRequest CELL = new SaveJournalCellRequest(10, null, null, null);

    @Test
    void rejectsLessonNotOwnedByTeacher() {
        when(lessonRepository.findForTeacher(3L, "teacher.one")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveCell("teacher.one", 3L, 7L, CELL))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode()).isEqualTo("JOURNAL_NOT_FOUND"));
        verify(gradeService, never()).save(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsStudentOutsideLessonClass() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(5L);
        ScheduleEntry schedule = new ScheduleEntry();
        schedule.setSchoolClass(schoolClass);
        Lesson lesson = new Lesson();
        lesson.setScheduleEntry(schedule);
        lesson.setStatus(LessonStatus.PLANNED);
        StudentProfile student = new StudentProfile();
        student.setId(7L);
        when(lessonRepository.findForTeacher(3L, "teacher.one")).thenReturn(Optional.of(lesson));
        when(studentProfileRepository.findById(7L)).thenReturn(Optional.of(student));
        when(membershipRepository.existsByStudentIdAndSchoolClassId(7L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.saveCell("teacher.one", 3L, 7L, CELL))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode()).isEqualTo("STUDENT_NOT_IN_CLASS"));
        verify(attendanceService, never()).save(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnnualWorkInFirstSemester() {
        LocalDate date = LocalDate.of(2026, 12, 18);
        Lesson lesson = lesson(date, LessonWorkType.CLASSWORK);
        stubOpenedLesson(lesson, date);

        assertThatThrownBy(() -> service.saveLesson("teacher.one", 10L, date,
            new SaveJournalLessonRequest(LessonWorkType.YEAR, null, null, null)))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("YEAR_GRADE_SECOND_SEMESTER_ONLY"));
        verify(gradeService, never()).retagLessonGrades(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsSecondSemesterWorkForSameClassAndSubject() {
        LocalDate date = LocalDate.of(2026, 12, 18);
        Lesson lesson = lesson(date, LessonWorkType.CLASSWORK);
        stubOpenedLesson(lesson, date);
        when(lessonRepository.existsOtherJournalWorkType(5L, 4L, LessonWorkType.SEMESTER,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31), 21L, LessonStatus.CANCELLED))
            .thenReturn(true);

        assertThatThrownBy(() -> service.saveLesson("teacher.one", 10L, date,
            new SaveJournalLessonRequest(LessonWorkType.SEMESTER, null, null, null)))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SEMESTER_GRADE_ALREADY_EXISTS"));
    }

    @Test
    void allowsOneAnnualWorkInSecondSemester() {
        LocalDate date = LocalDate.of(2027, 5, 20);
        Lesson lesson = lesson(date, LessonWorkType.CLASSWORK);
        stubOpenedLesson(lesson, date);
        when(clock.instant()).thenReturn(Instant.parse("2027-05-20T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        service.saveLesson("teacher.one", 10L, date,
            new SaveJournalLessonRequest(LessonWorkType.YEAR, "Annual assessment", null, null));

        assertThat(lesson.getWorkType()).isEqualTo(LessonWorkType.YEAR);
        verify(lessonRepository).existsOtherJournalWorkType(5L, 4L, LessonWorkType.YEAR,
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 5, 31), 21L, LessonStatus.CANCELLED);
        verify(gradeService).retagLessonGrades(lesson, GradeType.YEAR);
    }

    @Test
    void rejectsSecondAnnualWorkForSameClassAndSubject() {
        LocalDate date = LocalDate.of(2027, 5, 20);
        Lesson lesson = lesson(date, LessonWorkType.CLASSWORK);
        stubOpenedLesson(lesson, date);
        when(lessonRepository.existsOtherJournalWorkType(5L, 4L, LessonWorkType.YEAR,
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 5, 31), 21L, LessonStatus.CANCELLED))
            .thenReturn(true);

        assertThatThrownBy(() -> service.saveLesson("teacher.one", 10L, date,
            new SaveJournalLessonRequest(LessonWorkType.YEAR, null, null, null)))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("YEAR_GRADE_ALREADY_EXISTS"));
    }

    @Test
    void derivesStudentGradeTypeFromLessonWorkType() {
        Lesson lesson = lesson(LocalDate.of(2027, 5, 20), LessonWorkType.YEAR);
        StudentProfile student = new StudentProfile();
        student.setId(7L);
        student.setSchoolClass(lesson.getScheduleEntry().getSchoolClass());
        when(lessonRepository.findForTeacher(21L, "teacher.one")).thenReturn(Optional.of(lesson));
        when(studentProfileRepository.findById(7L)).thenReturn(Optional.of(student));

        service.saveCell("teacher.one", 21L, 7L, CELL);

        verify(gradeService).save(student, lesson, 10, GradeType.YEAR, null);
    }

    @Test
    void markAllPresentUpdatesEveryStudent() {
        LocalDate date = LocalDate.of(2026, 9, 24);
        Lesson lesson = lesson(date, LessonWorkType.CLASSWORK);
        stubOpenedLesson(lesson, date);
        StudentProfile previouslyAbsentStudent = new StudentProfile();
        previouslyAbsentStudent.setId(7L);
        StudentProfile unmarkedStudent = new StudentProfile();
        unmarkedStudent.setId(8L);
        when(studentProfileRepository.findAllBySchoolClassIdOrderBySurnameAscNameAsc(5L))
            .thenReturn(List.of(previouslyAbsentStudent, unmarkedStudent));

        service.markAllPresent("teacher.one", 10L, date);

        verify(attendanceService).save(previouslyAbsentStudent, lesson, AttendanceStatus.PRESENT);
        verify(attendanceService).save(unmarkedStudent, lesson, AttendanceStatus.PRESENT);
        verifyNoInteractions(gradeService, homeworkRepository, studentHomeworkRepository);
    }

    @Test
    void clearAttendanceLeavesGradesAndHomeworkUntouched() {
        Lesson lesson = lesson(LocalDate.of(2026, 9, 24), LessonWorkType.CLASSWORK);
        when(lessonRepository.findForTeacher(21L, "teacher.one")).thenReturn(Optional.of(lesson));
        Attendance attendance = new Attendance();
        when(attendanceRepository.findAllByLessonIdIn(List.of(21L))).thenReturn(List.of(attendance));

        service.clearAttendance("teacher.one", 21L);

        verify(attendanceRepository).deleteAll(List.of(attendance));
        verifyNoInteractions(gradeService, homeworkRepository, studentHomeworkRepository);
    }

    @Test
    void newScheduleDoesNotAddPastEmptySlotsAndPreservesRecordedLessons() {
        Lesson recorded = lesson(LocalDate.of(2026, 9, 14), LessonWorkType.CLASSWORK);
        var user = new com.ediary.backend.user.entity.AppUser();
        user.setLogin("teacher.one");
        var teacher = new com.ediary.backend.user.entity.TeacherProfile();
        teacher.setUser(user);
        ScheduleEntry oldEntry = recorded.getScheduleEntry();
        oldEntry.setId(11L);
        oldEntry.setTeacher(teacher);
        oldEntry.setLessonNumber(1);
        oldEntry.setActive(false);
        ScheduleEntry newEntry = new ScheduleEntry();
        newEntry.setId(10L);
        newEntry.setSchoolClass(oldEntry.getSchoolClass());
        newEntry.setSubject(oldEntry.getSubject());
        newEntry.setTeacher(teacher);
        newEntry.setLessonNumber(1);
        newEntry.setWeekNumber(1);
        newEntry.setWeekday(com.ediary.backend.schedule.entity.Weekday.MONDAY);
        newEntry.setEffectiveFrom(LocalDate.of(2026, 9, 28));
        when(scheduleRepository.findJournalAssignmentsForTeacher("teacher.one", "2026/2027"))
            .thenReturn(List.of(oldEntry, newEntry));
        when(lessonRepository.findJournalLessons(5L, 4L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            .thenReturn(List.of(recorded));

        var journal = service.find("teacher.one", 5L, 4L, "2026-09");

        assertThat(journal.lessons()).hasSize(2);
        assertThat(journal.lessons().getFirst().id()).isEqualTo(21L);
        assertThat(journal.lessons().getLast().scheduleId()).isEqualTo(10L);
        assertThat(journal.lessons().getLast().date()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    private Lesson lesson(LocalDate date, LessonWorkType workType) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(5L);
        Subject subject = new Subject();
        subject.setId(4L);
        ScheduleEntry schedule = new ScheduleEntry();
        schedule.setSchoolClass(schoolClass);
        schedule.setSubject(subject);
        schedule.setAcademicYear("2026/2027");
        Lesson lesson = new Lesson();
        lesson.setId(21L);
        lesson.setScheduleEntry(schedule);
        lesson.setDate(date);
        lesson.setStatus(LessonStatus.PLANNED);
        lesson.setWorkType(workType);
        lesson.setTopic("Lesson topic");
        return lesson;
    }

    @Test
    void rejectsRecordingWithoutTopicBeforeWritingStudentData() {
        Lesson lesson = lesson(LocalDate.of(2026, 9, 24), LessonWorkType.CLASSWORK);
        lesson.setTopic("   ");
        StudentProfile student = new StudentProfile();
        student.setId(7L);
        student.setSchoolClass(lesson.getScheduleEntry().getSchoolClass());
        when(lessonRepository.findForTeacher(21L, "teacher.one")).thenReturn(Optional.of(lesson));
        when(studentProfileRepository.findById(7L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.saveCell("teacher.one", 21L, 7L, CELL))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode()).isEqualTo("LESSON_TOPIC_REQUIRED"));
        verifyNoInteractions(gradeService, attendanceService, homeworkRepository, studentHomeworkRepository);
    }

    @Test
    void rejectsBulkAttendanceWithoutTopic() {
        LocalDate date = LocalDate.of(2026, 9, 24);
        Lesson lesson = lesson(date, LessonWorkType.CLASSWORK);
        lesson.setTopic(null);
        stubOpenedLesson(lesson, date);

        assertThatThrownBy(() -> service.markAllPresent("teacher.one", 10L, date))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode()).isEqualTo("LESSON_TOPIC_REQUIRED"));
        verifyNoInteractions(attendanceService);
    }

    private void stubOpenedLesson(Lesson lesson, LocalDate date) {
        LessonResponse opened = mock(LessonResponse.class);
        when(opened.id()).thenReturn(21L);
        when(lessonService.openForTeacher("teacher.one", 10L, date)).thenReturn(opened);
        when(lessonRepository.findForTeacher(21L, "teacher.one")).thenReturn(Optional.of(lesson));
    }
}
