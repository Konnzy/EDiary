package com.ediary.backend.school.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.dto.request.SaveScheduleLessonRequest;
import com.ediary.backend.school.dto.request.SaveScheduleRequest;
import com.ediary.backend.school.dto.response.AdminScheduleResponse;
import com.ediary.backend.school.dto.response.ScheduleLessonResponse;
import com.ediary.backend.school.entity.AcademicYear;
import com.ediary.backend.school.entity.ScheduleEntry;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.Subject;
import com.ediary.backend.school.entity.Weekday;
import com.ediary.backend.school.repository.AcademicYearRepository;
import com.ediary.backend.school.repository.ScheduleRepository;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.school.repository.SubjectRepository;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminScheduleService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<LocalTime[]> LESSON_TIMES = List.of(
        times("08:00", "08:45"),
        times("08:55", "09:40"),
        times("09:50", "10:35"),
        times("10:45", "11:30"),
        times("12:00", "12:45"),
        times("12:55", "13:40"),
        times("13:50", "14:35"),
        times("14:45", "15:30")
    );

    private final ScheduleRepository scheduleRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final AcademicYearRepository academicYearRepository;

    @Transactional(readOnly = true)
    public List<String> findAcademicYears() {
        return academicYearRepository.findAllByOrderByValueAsc().stream()
            .map(AcademicYear::getValue)
            .toList();
    }

    @Transactional
    public String createAcademicYear(String value) {
        validateAcademicYear(value);
        if (academicYearRepository.existsById(value)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "ACADEMIC_YEAR_ALREADY_EXISTS",
                "Такий навчальний рік уже існує"
            );
        }
        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue(value);
        return academicYearRepository.saveAndFlush(academicYear).getValue();
    }

    @Transactional(readOnly = true)
    public AdminScheduleResponse find(Long classId, String academicYear, Integer week) {
        requirePeriod(classId, academicYear, week);
        return toResponse(
            classId,
            academicYear,
            week,
            scheduleRepository
                .findAllBySchoolClass_IdAndAcademicYearAndWeekNumberOrderByWeekdayAscLessonNumberAsc(
                    classId,
                    academicYear,
                    week
                )
        );
    }

    @Transactional
    public AdminScheduleResponse save(
        Long classId,
        String academicYear,
        Integer week,
        SaveScheduleRequest request
    ) {
        SchoolClass schoolClass = requirePeriodForUpdate(classId, academicYear, week);
        Map<Slot, SaveScheduleLessonRequest> requestedBySlot = validateUniqueSlots(request.lessons());

        Set<Long> subjectIds = request.lessons().stream()
            .map(SaveScheduleLessonRequest::subjectId)
            .collect(Collectors.toSet());
        Set<Long> teacherIds = request.lessons().stream()
            .map(SaveScheduleLessonRequest::teacherId)
            .collect(Collectors.toSet());

        Map<Long, Subject> subjects = subjectIds.isEmpty()
            ? Map.of()
            : subjectRepository.findAllById(subjectIds).stream()
            .collect(Collectors.toMap(Subject::getId, Function.identity()));
        Map<Long, TeacherProfile> teachers = teacherIds.isEmpty()
            ? Map.of()
            : teacherProfileRepository.findAllForSchedule(teacherIds).stream()
            .collect(Collectors.toMap(TeacherProfile::getId, Function.identity()));
        validateAssignments(request.lessons(), subjects, teachers);
        validateTeacherConflicts(classId, academicYear, week, request.lessons());

        List<ScheduleEntry> currentEntries = scheduleRepository
            .findAllBySchoolClass_IdAndAcademicYearAndWeekNumberOrderByWeekdayAscLessonNumberAsc(
                classId,
                academicYear,
                week
            );
        Map<Slot, ScheduleEntry> currentBySlot = currentEntries.stream()
            .collect(Collectors.toMap(this::slot, Function.identity()));
        List<ScheduleEntry> savedEntries = new ArrayList<>();

        for (Map.Entry<Slot, SaveScheduleLessonRequest> requested : requestedBySlot.entrySet()) {
            SaveScheduleLessonRequest lesson = requested.getValue();
            ScheduleEntry entry = currentBySlot.remove(requested.getKey());
            if (entry == null) {
                entry = new ScheduleEntry();
                entry.setSchoolClass(schoolClass);
                entry.setAcademicYear(academicYear);
                entry.setWeekNumber(week);
                entry.setWeekday(lesson.weekday());
                entry.setLessonNumber(lesson.lessonNumber());
            }
            entry.setSubject(subjects.get(lesson.subjectId()));
            entry.setTeacher(teachers.get(lesson.teacherId()));
            entry.setRoom(lesson.room().strip());
            LocalTime[] times = LESSON_TIMES.get(lesson.lessonNumber() - 1);
            entry.setStartTime(times[0]);
            entry.setEndTime(times[1]);
            savedEntries.add(entry);
        }

        if (!currentBySlot.isEmpty()) {
            scheduleRepository.deleteAll(currentBySlot.values());
            scheduleRepository.flush();
        }
        savedEntries = scheduleRepository.saveAllAndFlush(savedEntries);
        savedEntries.sort((left, right) -> {
            int weekdayComparison = left.getWeekday().compareTo(right.getWeekday());
            return weekdayComparison != 0
                ? weekdayComparison
                : left.getLessonNumber().compareTo(right.getLessonNumber());
        });
        return toResponse(classId, academicYear, week, savedEntries);
    }

    private SchoolClass requirePeriodForUpdate(Long classId, String academicYear, Integer week) {
        validatePeriod(academicYear, week);
        if (!academicYearRepository.existsById(academicYear)) {
            throw academicYearNotFound();
        }
        return schoolClassRepository.findByIdForUpdate(classId)
            .orElseThrow(this::schoolClassNotFound);
    }

    private void requirePeriod(Long classId, String academicYear, Integer week) {
        validatePeriod(academicYear, week);
        if (!schoolClassRepository.existsById(classId)) {
            throw schoolClassNotFound();
        }
        if (!academicYearRepository.existsById(academicYear)) {
            throw academicYearNotFound();
        }
    }

    private void validatePeriod(String academicYear, Integer week) {
        validateAcademicYear(academicYear);
        if (week == null || week < 1 || week > 2) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SCHEDULE_WEEK",
                "Номер навчального тижня має бути 1 або 2"
            );
        }
    }

    private void validateAcademicYear(String value) {
        if (value == null || !value.matches("\\d{4}/\\d{4}")) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ACADEMIC_YEAR",
                "Навчальний рік має бути у форматі РРРР/РРРР"
            );
        }
        int start = Integer.parseInt(value.substring(0, 4));
        int end = Integer.parseInt(value.substring(5));
        if (end != start + 1) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ACADEMIC_YEAR",
                "Навчальний рік має містити два послідовні роки"
            );
        }
    }

    private Map<Slot, SaveScheduleLessonRequest> validateUniqueSlots(
        List<SaveScheduleLessonRequest> lessons
    ) {
        Map<Slot, SaveScheduleLessonRequest> result = new LinkedHashMap<>();
        for (SaveScheduleLessonRequest lesson : lessons) {
            if (lesson.weekday() == Weekday.SATURDAY || lesson.weekday() == Weekday.SUNDAY) {
                throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SCHEDULE_WEEKDAY",
                    "Розклад можна створити лише з понеділка до п’ятниці"
                );
            }
            if (result.put(new Slot(lesson.weekday(), lesson.lessonNumber()), lesson) != null) {
                throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_SCHEDULE_SLOT",
                    "Один слот розкладу не можна заповнити двічі"
                );
            }
        }
        return result;
    }

    private void validateAssignments(
        List<SaveScheduleLessonRequest> lessons,
        Map<Long, Subject> subjects,
        Map<Long, TeacherProfile> teachers
    ) {
        for (SaveScheduleLessonRequest lesson : lessons) {
            Subject subject = subjects.get(lesson.subjectId());
            TeacherProfile teacher = teachers.get(lesson.teacherId());
            if (subject == null) {
                throw new ApplicationException(HttpStatus.BAD_REQUEST, "SUBJECT_NOT_FOUND", "Предмет не знайдено");
            }
            if (teacher == null) {
                throw new ApplicationException(HttpStatus.BAD_REQUEST, "TEACHER_NOT_FOUND", "Учителя не знайдено");
            }
            if (teacher.getUser().getStatus() != UserStatus.ACTIVE) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "TEACHER_NOT_ACTIVE",
                    "До розкладу можна додати лише активного вчителя"
                );
            }
            boolean teachesSubject = teacher.getSubjects().stream()
                .anyMatch(assignedSubject -> assignedSubject.getId().equals(subject.getId()));
            if (!teachesSubject) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "TEACHER_SUBJECT_NOT_ASSIGNED",
                    "Обраний учитель не призначений до цього предмета"
                );
            }
        }
    }

    private void validateTeacherConflicts(
        Long classId,
        String academicYear,
        Integer week,
        List<SaveScheduleLessonRequest> lessons
    ) {
        Set<TeacherSlot> occupied = scheduleRepository
            .findTeacherConflicts(classId, academicYear, week)
            .stream()
            .map(entry -> new TeacherSlot(entry.getTeacher().getId(), entry.getWeekday(), entry.getLessonNumber()))
            .collect(Collectors.toCollection(HashSet::new));
        for (SaveScheduleLessonRequest lesson : lessons) {
            if (occupied.contains(new TeacherSlot(lesson.teacherId(), lesson.weekday(), lesson.lessonNumber()))) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "TEACHER_SCHEDULE_CONFLICT",
                    "Обраний учитель уже має урок у цей час"
                );
            }
        }
    }

    private AdminScheduleResponse toResponse(
        Long classId,
        String academicYear,
        Integer week,
        List<ScheduleEntry> entries
    ) {
        return new AdminScheduleResponse(
            classId,
            academicYear,
            week,
            entries.stream().map(this::toLessonResponse).toList()
        );
    }

    private ScheduleLessonResponse toLessonResponse(ScheduleEntry entry) {
        return new ScheduleLessonResponse(
            entry.getId(),
            entry.getWeekday(),
            entry.getLessonNumber(),
            entry.getSubject().getId(),
            entry.getSubject().getTitle(),
            entry.getTeacher().getId(),
            fullName(entry.getTeacher()),
            entry.getRoom(),
            entry.getStartTime().format(TIME_FORMAT),
            entry.getEndTime().format(TIME_FORMAT)
        );
    }

    private Slot slot(ScheduleEntry entry) {
        return new Slot(entry.getWeekday(), entry.getLessonNumber());
    }

    private ApplicationException schoolClassNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "SCHOOL_CLASS_NOT_FOUND", "Клас не знайдено");
    }

    private ApplicationException academicYearNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "ACADEMIC_YEAR_NOT_FOUND", "Навчальний рік не знайдено");
    }

    private String fullName(TeacherProfile teacher) {
        return teacher.getSurname() + " " + teacher.getName()
            + (teacher.getPatronymic() == null || teacher.getPatronymic().isBlank()
                ? ""
                : " " + teacher.getPatronymic());
    }

    private static LocalTime[] times(String start, String end) {
        return new LocalTime[]{LocalTime.parse(start), LocalTime.parse(end)};
    }

    private record Slot(Weekday weekday, Integer lessonNumber) {
    }

    private record TeacherSlot(Long teacherId, Weekday weekday, Integer lessonNumber) {
    }
}
