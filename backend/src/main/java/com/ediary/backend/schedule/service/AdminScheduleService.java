package com.ediary.backend.schedule.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.dto.request.SaveScheduleLessonRequest;
import com.ediary.backend.schedule.dto.request.SaveScheduleRequest;
import com.ediary.backend.schedule.dto.response.AdminScheduleResponse;
import com.ediary.backend.schedule.dto.response.ScheduleLessonResponse;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.entity.Weekday;
import com.ediary.backend.schedule.repository.AcademicYearRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.notification.service.UserNotificationService;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private final StudentProfileRepository studentProfileRepository;
    private final UserNotificationService notificationService;
    private final Clock clock;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<String> findAcademicYears() {
        return academicYearRepository.findAllByOrderByValueAsc().stream()
            .map(AcademicYear::getValue)
            .toList();
    }

    @Transactional
    public String createAcademicYear(String value) {
        validateAcademicYear(value);
        int requestedStartYear = Integer.parseInt(value.substring(0, 4));
        int currentStartYear = currentAcademicYearStart(LocalDate.now(clock));
        if (requestedStartYear > currentStartYear) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "ACADEMIC_YEAR_NOT_STARTED",
                "Навчальний рік ще не настав. Його можна створити не раніше 1 вересня "
                    + requestedStartYear + " року"
            );
        }
        if (academicYearRepository.existsById(value)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "ACADEMIC_YEAR_ALREADY_EXISTS",
                "Такий навчальний рік уже існує"
            );
        }
        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue(value);
        String saved = academicYearRepository.saveAndFlush(academicYear).getValue();
        audit.record("ACADEMIC_YEAR_CREATED", "ACADEMIC_YEAR", saved, of(), of("created", true));
        return saved;
    }

    @Transactional(readOnly = true)
    public AdminScheduleResponse find(Long classId, String academicYear, Integer week) {
        requirePeriod(classId, academicYear, week);
        return toResponse(
            classId,
            academicYear,
            week,
            scheduleRepository
                .findAllBySchoolClass_IdAndAcademicYearAndWeekNumberAndActiveTrueOrderByWeekdayAscLessonNumberAsc(
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
        if (schoolClass.isDraft()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CLASS_TEACHER_REQUIRED",
                "Спочатку призначте класного керівника, щоб складати розклад"
            );
        }
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

        List<ScheduleEntry> currentEntries = scheduleRepository.findActiveForUpdate(classId, academicYear, week);
        var beforeSchedule = auditSchedule(currentEntries);
        var emailBefore = com.ediary.backend.notification.service.ScheduleChangeText.capture(currentEntries);
        Map<Slot, ScheduleDefinition> previousDefinitions = currentEntries.stream().collect(Collectors.toMap(
            this::slot, entry -> new ScheduleDefinition(entry.getSubject().getId(),
                entry.getTeacher().getId(), entry.getRoom())
        ));
        Map<Slot, ScheduleDefinition> requestedDefinitions = requestedBySlot.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, item -> new ScheduleDefinition(item.getValue().subjectId(),
                item.getValue().teacherId(), normalizeRoom(item.getValue().room()))
        ));
        boolean changed = !previousDefinitions.equals(requestedDefinitions);
        Map<Long, TeacherProfile> previousTeachers = currentEntries.stream().map(ScheduleEntry::getTeacher)
            .collect(Collectors.toMap(TeacherProfile::getId, Function.identity(), (first, ignored) -> first));
        Set<Long> referencedIds = currentEntries.isEmpty()
            ? Set.of()
            : new HashSet<>(scheduleRepository.findIdsReferencedByLessons(
                currentEntries.stream().map(ScheduleEntry::getId).toList()
            ));
        Map<Slot, ScheduleEntry> currentBySlot = currentEntries.stream()
            .collect(Collectors.toMap(this::slot, Function.identity()));
        List<ScheduleEntry> savedEntries = new ArrayList<>();
        List<ScheduleEntry> archivedEntries = new ArrayList<>();

        for (Map.Entry<Slot, SaveScheduleLessonRequest> requested : requestedBySlot.entrySet()) {
            SaveScheduleLessonRequest lesson = requested.getValue();
            ScheduleEntry entry = currentBySlot.remove(requested.getKey());
            if (entry != null && referencedIds.contains(entry.getId()) && !sameDefinition(entry, lesson)) {
                archive(entry, archivedEntries);
                entry = null;
            }
            if (entry == null) {
                entry = new ScheduleEntry();
                entry.setEffectiveFrom(LocalDate.now(clock));
                entry.setSchoolClass(schoolClass);
                entry.setAcademicYear(academicYear);
                entry.setWeekNumber(week);
                entry.setWeekday(lesson.weekday());
                entry.setLessonNumber(lesson.lessonNumber());
            }
            if (entry.getId() == null || !referencedIds.contains(entry.getId())) {
                if (entry.getId() != null && !sameDefinition(entry, lesson)) {
                    entry.setEffectiveFrom(LocalDate.now(clock));
                }
                entry.setSubject(subjects.get(lesson.subjectId()));
                entry.setTeacher(teachers.get(lesson.teacherId()));
                entry.setRoom(normalizeRoom(lesson.room()));
                LocalTime[] times = LESSON_TIMES.get(lesson.lessonNumber() - 1);
                entry.setStartTime(times[0]);
                entry.setEndTime(times[1]);
            }
            savedEntries.add(entry);
        }

        List<ScheduleEntry> entriesToDelete = new ArrayList<>();
        for (ScheduleEntry entry : currentBySlot.values()) {
            if (referencedIds.contains(entry.getId())) {
                archive(entry, archivedEntries);
            } else {
                entriesToDelete.add(entry);
            }
        }
        if (!archivedEntries.isEmpty()) {
            scheduleRepository.saveAllAndFlush(archivedEntries);
        }
        if (!entriesToDelete.isEmpty()) {
            scheduleRepository.deleteAll(entriesToDelete);
            scheduleRepository.flush();
        }
        savedEntries = scheduleRepository.saveAllAndFlush(savedEntries);
        savedEntries.sort(Comparator.comparing(ScheduleEntry::getWeekday).thenComparingInt(ScheduleEntry::getLessonNumber));
        if (changed) {
            var emailAfter = com.ediary.backend.notification.service.ScheduleChangeText.capture(savedEntries);
            String context = "Клас: "+schoolClass.getDisplayName()+"\nНавчальний рік: "+academicYear+"\nНавчальний тиждень: "+week+"\n\n";
            String body = context+com.ediary.backend.notification.service.ScheduleChangeText.describe(emailBefore,emailAfter,null,LocalDate.now(clock));
            Map<Long, TeacherProfile> affectedTeachers = new HashMap<>(previousTeachers);
            savedEntries.stream().map(ScheduleEntry::getTeacher).forEach(teacher -> affectedTeachers.put(teacher.getId(), teacher));
            affectedTeachers.values().forEach(teacher -> {
                String changes=com.ediary.backend.notification.service.ScheduleChangeText.describe(emailBefore,emailAfter,teacher.getId(),LocalDate.now(clock));
                if(!changes.isBlank())notificationService.forTeacher(teacher,
                    "SCHEDULE", "Зміна розкладу", context+changes, "teacher/schedule", null);
            });
            notificationService.forTeacher(schoolClass.getClassTeacher(), "CLASS_SCHEDULE",
                "Зміна розкладу класу", body, "teacher/schedule?classId=" + classId, null);
            studentProfileRepository.findAllBySchoolClassIdOrderBySurnameAscNameAsc(classId).forEach(student ->
                notificationService.forStudent(student, "SCHEDULE", "Зміна розкладу", body, null));
        }
        audit.record("SCHEDULE_SAVED", "SCHEDULE", classId + ":" + academicYear + ":" + week,
            of("lessons", beforeSchedule, "count", beforeSchedule.size()),
            of("lessons", auditSchedule(savedEntries), "count", savedEntries.size()));
        return toResponse(classId, academicYear, week, savedEntries);
    }

    private List<Map<String, Object>> auditSchedule(List<ScheduleEntry> entries) {
        return entries.stream().sorted(Comparator.comparing(ScheduleEntry::getWeekday).thenComparingInt(ScheduleEntry::getLessonNumber))
            .map(entry -> of("day", entry.getWeekday(), "number", entry.getLessonNumber(), "subjectId", entry.getSubject().getId(),
                "teacherId", entry.getTeacher().getId(), "room", entry.getRoom(), "start", entry.getStartTime(), "end", entry.getEndTime())).toList();
    }

    private boolean sameDefinition(ScheduleEntry entry, SaveScheduleLessonRequest lesson) {
        LocalTime[] times = LESSON_TIMES.get(lesson.lessonNumber() - 1);
        return entry.getSubject().getId().equals(lesson.subjectId())
            && entry.getTeacher().getId().equals(lesson.teacherId())
            && Objects.equals(entry.getRoom(), normalizeRoom(lesson.room()))
            && entry.getStartTime().equals(times[0])
            && entry.getEndTime().equals(times[1]);
    }

    private String normalizeRoom(String room) {
        return room.strip().replaceAll("\\s+", " ").replaceAll("\\s*-\\s*", "-");
    }

    private void archive(ScheduleEntry entry, List<ScheduleEntry> archivedEntries) {
        entry.setActive(false);
        entry.setArchivedAt(java.time.LocalDateTime.now(clock));
        archivedEntries.add(entry);
    }

    private SchoolClass requirePeriodForUpdate(Long classId, String academicYear, Integer week) {
        validatePeriod(academicYear, week);
        if (!academicYearRepository.existsById(academicYear)) {
            throw academicYearNotFound();
        }
        SchoolClass schoolClass = schoolClassRepository.findByIdForUpdate(classId)
            .orElseThrow(this::schoolClassNotFound);
        ensureClassBelongsToAcademicYear(schoolClass, academicYear);
        return schoolClass;
    }

    private void requirePeriod(Long classId, String academicYear, Integer week) {
        validatePeriod(academicYear, week);
        if (!academicYearRepository.existsById(academicYear)) {
            throw academicYearNotFound();
        }
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
            .orElseThrow(this::schoolClassNotFound);
        ensureClassBelongsToAcademicYear(schoolClass, academicYear);
    }

    private void ensureClassBelongsToAcademicYear(SchoolClass schoolClass, String academicYear) {
        if (!schoolClass.getAcademicYear().getValue().equals(academicYear)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CLASS_ACADEMIC_YEAR_MISMATCH",
                "Обраний клас не належить до цього навчального року"
            );
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

    private int currentAcademicYearStart(LocalDate date) {
        return date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
    }

    private record Slot(Weekday weekday, Integer lessonNumber) {
    }

    private record ScheduleDefinition(Long subjectId, Long teacherId, String room) {
    }

    private record TeacherSlot(Long teacherId, Weekday weekday, Integer lessonNumber) {
    }
}
