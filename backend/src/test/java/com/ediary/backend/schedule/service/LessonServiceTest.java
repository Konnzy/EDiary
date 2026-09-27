package com.ediary.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.dto.request.UpdateLessonRequest;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.entity.Weekday;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.TeacherProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 9, 14);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 7, 30);

    @Mock private LessonRepository lessonRepository;
    @Mock private ScheduleRepository scheduleRepository;

    private LessonService service;

    @BeforeEach
    void setUp() {
        service = new LessonService(
            lessonRepository,
            scheduleRepository,
            Clock.fixed(Instant.parse("2026-09-14T07:30:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsCreatingLessonBeforeScheduleTookEffect() {
        ScheduleEntry schedule = schedule("teacher.one", 1, Weekday.MONDAY);
        schedule.setEffectiveFrom(LESSON_DATE.plusDays(1));
        when(scheduleRepository.findByIdForLessonCreation(10L)).thenReturn(Optional.of(schedule));
        when(lessonRepository.findByScheduleEntry_IdAndDate(10L, LESSON_DATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openForTeacher("teacher.one", 10L, LESSON_DATE))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode()).isEqualTo("SCHEDULE_NOT_EFFECTIVE"));
        verify(lessonRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void opensLessonFromMatchingScheduleEntry() {
        ScheduleEntry schedule = schedule("teacher.one", 1, Weekday.MONDAY);
        when(scheduleRepository.findByIdForLessonCreation(10L)).thenReturn(Optional.of(schedule));
        when(lessonRepository.findByScheduleEntry_IdAndDate(10L, LESSON_DATE))
            .thenReturn(Optional.empty());
        when(lessonRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Lesson.class)))
            .thenAnswer(invocation -> {
                Lesson lesson = invocation.getArgument(0);
                lesson.setId(20L);
                return lesson;
            });

        var response = service.openForTeacher("teacher.one", 10L, LESSON_DATE);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(LessonStatus.PLANNED);
        assertThat(response.className()).isEqualTo("9-А");
        assertThat(response.subject()).isEqualTo("Математика");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void returnsExistingLessonInsteadOfCreatingDuplicate() {
        ScheduleEntry schedule = schedule("teacher.one", 1, Weekday.MONDAY);
        Lesson existing = lesson(schedule, LessonStatus.PLANNED);
        when(scheduleRepository.findByIdForLessonCreation(10L)).thenReturn(Optional.of(schedule));
        when(lessonRepository.findByScheduleEntry_IdAndDate(10L, LESSON_DATE))
            .thenReturn(Optional.of(existing));

        var response = service.openForTeacher("teacher.one", 10L, LESSON_DATE);

        assertThat(response.id()).isEqualTo(20L);
        verify(lessonRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsScheduleAssignedToAnotherTeacher() {
        ScheduleEntry schedule = schedule("teacher.other", 1, Weekday.MONDAY);
        when(scheduleRepository.findByIdForLessonCreation(10L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.openForTeacher("teacher.one", 10L, LESSON_DATE))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("LESSON_ACCESS_DENIED"));
        verify(lessonRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDateThatDoesNotMatchScheduleWeek() {
        ScheduleEntry schedule = schedule("teacher.one", 2, Weekday.MONDAY);
        when(scheduleRepository.findByIdForLessonCreation(10L)).thenReturn(Optional.of(schedule));
        when(lessonRepository.findByScheduleEntry_IdAndDate(10L, LESSON_DATE))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openForTeacher("teacher.one", 10L, LESSON_DATE))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("LESSON_DATE_DOES_NOT_MATCH_SCHEDULE"));
    }

    @Test
    void rejectsNewLessonDuringVacation() {
        LocalDate vacationDate = LocalDate.of(2026, 10, 26);
        ScheduleEntry schedule = schedule("teacher.one", 1, Weekday.MONDAY);
        when(scheduleRepository.findByIdForLessonCreation(10L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.openForTeacher("teacher.one", 10L, vacationDate))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SCHOOL_VACATION"));
        verify(lessonRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completesLessonAndNormalizesTopic() {
        Lesson lesson = lesson(schedule("teacher.one", 1, Weekday.MONDAY), LessonStatus.PLANNED);
        when(lessonRepository.findForTeacher(20L, "teacher.one")).thenReturn(Optional.of(lesson));
        when(lessonRepository.saveAndFlush(lesson)).thenReturn(lesson);

        var response = service.updateForTeacher(
            "teacher.one",
            20L,
            new UpdateLessonRequest("  Квадратні   рівняння  ", LessonStatus.COMPLETED)
        );

        assertThat(response.topic()).isEqualTo("Квадратні рівняння");
        assertThat(response.status()).isEqualTo(LessonStatus.COMPLETED);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void requiresTopicBeforeCompletingLesson() {
        Lesson lesson = lesson(schedule("teacher.one", 1, Weekday.MONDAY), LessonStatus.PLANNED);
        when(lessonRepository.findForTeacher(20L, "teacher.one")).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.updateForTeacher(
            "teacher.one",
            20L,
            new UpdateLessonRequest(" ", LessonStatus.COMPLETED)
        ))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("LESSON_TOPIC_REQUIRED"));
    }

    @Test
    void preventsReopeningCompletedLesson() {
        Lesson lesson = lesson(schedule("teacher.one", 1, Weekday.MONDAY), LessonStatus.COMPLETED);
        lesson.setTopic("Квадратні рівняння");
        when(lessonRepository.findForTeacher(20L, "teacher.one")).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.updateForTeacher(
            "teacher.one",
            20L,
            new UpdateLessonRequest(lesson.getTopic(), LessonStatus.IN_PROGRESS)
        ))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("INVALID_LESSON_STATUS_TRANSITION"));
    }

    private ScheduleEntry schedule(String teacherLogin, int week, Weekday weekday) {
        AppUser user = new AppUser();
        user.setLogin(teacherLogin);
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(3L);
        teacher.setUser(user);
        teacher.setName("Олександр");
        teacher.setSurname("Коваленко");

        Subject subject = new Subject();
        subject.setId(4L);
        subject.setTitle("Математика");

        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue("2026/2027");
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(5L);
        schoolClass.setAcademicYear(academicYear);
        schoolClass.setNumber(9);
        schoolClass.setLetter("А");

        ScheduleEntry schedule = new ScheduleEntry();
        schedule.setId(10L);
        schedule.setTeacher(teacher);
        schedule.setSubject(subject);
        schedule.setSchoolClass(schoolClass);
        schedule.setAcademicYear("2026/2027");
        schedule.setWeekNumber(week);
        schedule.setWeekday(weekday);
        schedule.setLessonNumber(1);
        schedule.setRoom("121-А");
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(8, 45));
        schedule.setActive(true);
        return schedule;
    }

    private Lesson lesson(ScheduleEntry schedule, LessonStatus status) {
        Lesson lesson = new Lesson();
        lesson.setId(20L);
        lesson.setScheduleEntry(schedule);
        lesson.setDate(LESSON_DATE);
        lesson.setStatus(status);
        lesson.setCreatedAt(NOW.minusHours(1));
        lesson.setUpdatedAt(NOW.minusHours(1));
        return lesson;
    }
}
