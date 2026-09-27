package com.ediary.backend.journal.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.dto.JournalOptionsResponse;
import com.ediary.backend.journal.dto.JournalResponse;
import com.ediary.backend.journal.dto.SaveJournalCellRequest;
import com.ediary.backend.journal.dto.SaveJournalLessonRequest;
import com.ediary.backend.journal.entity.Attendance;
import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.entity.Grade;
import com.ediary.backend.journal.entity.GradeType;
import com.ediary.backend.journal.entity.Homework;
import com.ediary.backend.journal.entity.HomeworkStatus;
import com.ediary.backend.journal.entity.LessonWorkType;
import com.ediary.backend.journal.entity.StudentHomework;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.journal.repository.HomeworkRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.Weekday;
import com.ediary.backend.schedule.repository.AcademicYearRepository;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.schedule.service.LessonService;
import com.ediary.backend.schedule.service.LessonTopicPolicy;
import com.ediary.backend.schedule.service.SchedulePeriod;
import com.ediary.backend.schedule.service.SchoolVacationCalendar;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JournalService {

    private final ScheduleRepository scheduleRepository;
    private final LessonRepository lessonRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentClassMembershipRepository membershipRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HomeworkRepository homeworkRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final GradeService gradeService;
    private final AttendanceService attendanceService;
    private final LessonService lessonService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public JournalOptionsResponse options(String teacherLogin, String academicYear) {
        String currentYear = SchedulePeriod.academicYear(LocalDate.now(clock));
        String selectedYear = academicYear == null ? currentYear : academicYear;
        Map<Long, JournalOptionsResponse.ClassOption> classes = new LinkedHashMap<>();
        Map<Long, Map<Long, JournalOptionsResponse.SubjectOption>> subjects = new LinkedHashMap<>();
        for (ScheduleEntry entry : scheduleRepository.findJournalAssignmentsForTeacher(teacherLogin, selectedYear)) {
            Long classId = entry.getSchoolClass().getId();
            subjects.computeIfAbsent(classId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(entry.getSubject().getId(), new JournalOptionsResponse.SubjectOption(
                    entry.getSubject().getId(), entry.getSubject().getTitle()
                ));
            classes.putIfAbsent(classId, new JournalOptionsResponse.ClassOption(
                classId, entry.getSchoolClass().getDisplayName(), List.of()
            ));
        }
        List<JournalOptionsResponse.ClassOption> result = classes.values().stream()
            .map(item -> new JournalOptionsResponse.ClassOption(
                item.id(), item.label(), subjects.get(item.id()).values().stream()
                    .sorted(Comparator.comparing(JournalOptionsResponse.SubjectOption::label))
                    .toList()
            ))
            .sorted(Comparator.comparing(JournalOptionsResponse.ClassOption::label))
            .toList();
        return new JournalOptionsResponse(
            academicYearRepository.findAllByOrderByValueAsc().stream().map(year -> year.getValue()).toList(),
            currentYear,
            result
        );
    }

    @Transactional(readOnly = true)
    public JournalResponse find(String teacherLogin, Long classId, Long subjectId, String month) {
        YearMonth period = parseMonth(month);
        String year = SchedulePeriod.academicYear(period.atDay(1));
        List<ScheduleEntry> assignments = scheduleRepository.findJournalAssignmentsForTeacher(teacherLogin, year)
            .stream()
            .filter(entry -> entry.getSchoolClass().getId().equals(classId)
                && entry.getSubject().getId().equals(subjectId))
            .toList();
        if (assignments.isEmpty()) throw journalNotFound();

        List<StudentProfile> students = studentProfileRepository.findAllBySchoolClassIdOrderBySurnameAscNameAsc(classId);
        Map<String, JournalSlot> slots = new LinkedHashMap<>();
        for (ScheduleEntry entry : assignments) {
            if (!entry.isActive()) continue;
            for (int day = 1; day <= period.lengthOfMonth(); day++) {
                LocalDate date = period.atDay(day);
                if ((entry.getEffectiveFrom() == null || !date.isBefore(entry.getEffectiveFrom()))
                    && !SchoolVacationCalendar.isVacation(date)
                    && entry.getWeekNumber() == SchedulePeriod.weekNumber(date)
                    && entry.getWeekday() == Weekday.valueOf(date.getDayOfWeek().name())) {
                    slots.put(slotKey(entry.getId(), date), new JournalSlot(entry, date, null));
                }
            }
        }
        List<Lesson> existing = lessonRepository.findJournalLessons(
            classId, subjectId, period.atDay(1), period.atEndOfMonth()
        ).stream().filter(lesson -> lesson.getScheduleEntry().getTeacher().getUser().getLogin()
            .equalsIgnoreCase(teacherLogin)).toList();
        for (Lesson lesson : existing) {
            slots.put(slotKey(lesson.getScheduleEntry().getId(), lesson.getDate()),
                new JournalSlot(lesson.getScheduleEntry(), lesson.getDate(), lesson));
        }

        List<Long> lessonIds = existing.stream().map(Lesson::getId).toList();
        List<Grade> grades = lessonIds.isEmpty() ? List.of() : gradeRepository.findAllByLessonIdIn(lessonIds);
        List<Attendance> attendances = lessonIds.isEmpty() ? List.of() : attendanceRepository.findAllByLessonIdIn(lessonIds);
        List<Homework> homework = lessonIds.isEmpty() ? List.of() : homeworkRepository.findAllByLessonIdIn(lessonIds);
        List<Long> homeworkIds = homework.stream().map(Homework::getId).toList();
        List<StudentHomework> homeworkStatuses = homeworkIds.isEmpty()
            ? List.of() : studentHomeworkRepository.findAllByHomeworkIdIn(homeworkIds);
        Map<Long, Homework> homeworkByLesson = homework.stream()
            .collect(Collectors.toMap(item -> item.getLesson().getId(), item -> item, (first, ignored) -> first));
        Map<String, Grade> gradesByCell = grades.stream()
            .collect(Collectors.toMap(item -> cellKey(item.getLesson().getId(), item.getStudent().getId()),
                item -> item, (first, ignored) -> first));
        Map<String, Attendance> attendanceByCell = attendances.stream()
            .collect(Collectors.toMap(item -> cellKey(item.getLesson().getId(), item.getStudent().getId()),
                item -> item, (first, ignored) -> first));
        Map<String, StudentHomework> homeworkByCell = homeworkStatuses.stream()
            .collect(Collectors.toMap(item -> cellKey(item.getHomework().getId(), item.getStudent().getId()),
                item -> item, (first, ignored) -> first));

        List<JournalResponse.Lesson> lessons = slots.values().stream()
            .sorted(Comparator.comparing(JournalSlot::date)
                .thenComparing(slot -> slot.entry().getLessonNumber()))
            .map(slot -> {
                Lesson lesson = slot.lesson();
                Homework assignment = lesson == null ? null : homeworkByLesson.get(lesson.getId());
                List<JournalResponse.Cell> cells = new ArrayList<>();
                if (lesson != null) {
                    for (StudentProfile student : students) {
                        String key = cellKey(lesson.getId(), student.getId());
                        Grade grade = gradesByCell.get(key);
                        Attendance attendance = attendanceByCell.get(key);
                        StudentHomework studentHomework = assignment == null ? null
                            : homeworkByCell.get(cellKey(assignment.getId(), student.getId()));
                        if (grade != null || attendance != null || studentHomework != null) {
                            cells.add(new JournalResponse.Cell(
                                student.getId(),
                                grade == null ? null : grade.getScore(),
                                attendance == null ? null : attendance.getStatus(),
                                studentHomework == null ? null : studentHomework.getStatus(),
                                grade == null ? null : grade.getComment()
                            ));
                        }
                    }
                }
                return new JournalResponse.Lesson(
                    lesson == null ? null : lesson.getId(), slot.entry().getId(), slot.date(),
                    slot.entry().getLessonNumber(),
                    lesson == null ? LessonWorkType.CLASSWORK : lesson.getWorkType(),
                    lesson == null ? null : lesson.getTopic(),
                    assignment == null ? null : assignment.getDescription(),
                    assignment == null ? null : assignment.getDeadline(),
                    cells
                );
            }).toList();

        ScheduleEntry first = assignments.getFirst();
        return new JournalResponse(classId, first.getSchoolClass().getDisplayName(), subjectId,
            first.getSubject().getTitle(), month,
            students.stream().map(student -> new JournalResponse.Student(
                student.getId(), student.getSurname() + " " + student.getName()
            )).toList(), lessons);
    }

    @Transactional
    public void saveLesson(String teacherLogin, Long scheduleId, LocalDate date, SaveJournalLessonRequest request) {
        Long lessonId = lessonService.openForTeacher(teacherLogin, scheduleId, date).id();
        Lesson lesson = lessonRepository.findForTeacher(lessonId, teacherLogin).orElseThrow(this::journalNotFound);
        if (lesson.getStatus() == com.ediary.backend.schedule.entity.LessonStatus.CANCELLED) {
            throw new ApplicationException(HttpStatus.CONFLICT, "LESSON_CANCELLED", "Скасований урок не можна редагувати");
        }
        String homeworkText = normalize(request.homework());
        if (homeworkText != null && request.homeworkDeadline() != null
            && request.homeworkDeadline().isBefore(date)) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_HOMEWORK_DEADLINE",
                "Термін домашнього завдання не може передувати уроку");
        }
        validateFinalWorkType(lesson, request.workType());
        String topic = normalize(request.topic());
        if (topic == null) {
            LessonTopicPolicy.validate(lesson, topic, LocalDate.now(clock),
                homeworkText != null || lessonRepository.hasJournalRecords(lessonId));
        }
        gradeService.retagLessonGrades(lesson, gradeType(request.workType()));
        lesson.setWorkType(request.workType());
        lesson.setTopic(topic);
        lesson.setUpdatedAt(LocalDateTime.now(clock));
        lessonRepository.save(lesson);

        Homework homework = homeworkRepository.findFirstByLessonId(lessonId).orElse(null);
        if (homeworkText == null) {
            if (homework != null) {
                studentHomeworkRepository.deleteAll(studentHomeworkRepository.findAllByHomeworkId(homework.getId()));
                homeworkRepository.delete(homework);
            }
        } else {
            if (homework == null) {
                homework = new Homework();
                homework.setLesson(lesson);
                homework.setDate(date);
            }
            homework.setDescription(homeworkText);
            homework.setDeadline(request.homeworkDeadline());
            homeworkRepository.save(homework);
        }
    }

    @Transactional
    public void saveCell(String teacherLogin, Long lessonId, Long studentId, SaveJournalCellRequest request) {
        Lesson lesson = lessonRepository.findForTeacher(lessonId, teacherLogin).orElseThrow(this::journalNotFound);
        if (lesson.getStatus() == com.ediary.backend.schedule.entity.LessonStatus.CANCELLED) {
            throw new ApplicationException(HttpStatus.CONFLICT, "LESSON_CANCELLED", "Скасований урок не можна редагувати");
        }
        StudentProfile student = studentProfileRepository.findById(studentId)
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Учня не знайдено"));
        Long classId = lesson.getScheduleEntry().getSchoolClass().getId();
        if (!membershipRepository.existsByStudentIdAndSchoolClassId(studentId, classId)
            && (student.getSchoolClass() == null || !classId.equals(student.getSchoolClass().getId()))) {
            throw new ApplicationException(HttpStatus.CONFLICT, "STUDENT_NOT_IN_CLASS",
                "Учень не належить до класу цього уроку");
        }
        if (request.score() != null || request.attendanceStatus() != null || request.homeworkStatus() != null) {
            LessonTopicPolicy.requireTopic(lesson.getTopic());
        }
        gradeService.save(student, lesson, request.score(), gradeType(lesson.getWorkType()), request.comment());
        attendanceService.save(student, lesson, request.attendanceStatus());

        Homework homework = homeworkRepository.findFirstByLessonId(lessonId).orElse(null);
        if (request.homeworkStatus() != null && homework == null) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "HOMEWORK_NOT_ASSIGNED",
                "Для уроку не призначено домашнього завдання");
        }
        if (homework != null) {
            StudentHomework studentHomework = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(studentId, homework.getId()).orElse(null);
            if (request.homeworkStatus() == null) {
                if (studentHomework != null) studentHomeworkRepository.delete(studentHomework);
            } else {
                if (studentHomework == null) {
                    studentHomework = new StudentHomework();
                    studentHomework.setStudent(student);
                    studentHomework.setHomework(homework);
                }
                studentHomework.setStatus(request.homeworkStatus());
                studentHomework.setUpdatedAt(LocalDateTime.now(clock));
                studentHomeworkRepository.save(studentHomework);
            }
        }
    }

    @Transactional
    public void markAllPresent(String teacherLogin, Long scheduleId, LocalDate date) {
        Long lessonId = lessonService.openForTeacher(teacherLogin, scheduleId, date).id();
        Lesson lesson = lessonRepository.findForTeacher(lessonId, teacherLogin).orElseThrow(this::journalNotFound);
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw new ApplicationException(HttpStatus.CONFLICT, "LESSON_CANCELLED", "Скасований урок не можна редагувати");
        }
        LessonTopicPolicy.requireTopic(lesson.getTopic());

        for (StudentProfile student : studentProfileRepository.findAllBySchoolClassIdOrderBySurnameAscNameAsc(
            lesson.getScheduleEntry().getSchoolClass().getId()
        )) {
            attendanceService.save(student, lesson, AttendanceStatus.PRESENT);
        }
    }

    @Transactional
    public void clearAttendance(String teacherLogin, Long lessonId) {
        Lesson lesson = lessonRepository.findForTeacher(lessonId, teacherLogin).orElseThrow(this::journalNotFound);
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw new ApplicationException(HttpStatus.CONFLICT, "LESSON_CANCELLED", "Скасований урок не можна редагувати");
        }
        attendanceRepository.deleteAll(attendanceRepository.findAllByLessonIdIn(List.of(lessonId)));
    }

    private GradeType gradeType(LessonWorkType workType) {
        return switch (workType) {
            case CONTROL -> GradeType.TEST;
            case HOMEWORK -> GradeType.HOMEWORK;
            case INDEPENDENT -> GradeType.INDEPENDENT_WORK;
            case THEMATIC -> GradeType.TOPIC;
            case SEMESTER -> GradeType.SEMESTER;
            case YEAR -> GradeType.YEAR;
            default -> GradeType.CURRENT;
        };
    }

    private void validateFinalWorkType(Lesson lesson, LessonWorkType workType) {
        if (workType != LessonWorkType.SEMESTER && workType != LessonWorkType.YEAR) return;
        LocalDate date = lesson.getDate();
        int startYear = Integer.parseInt(lesson.getScheduleEntry().getAcademicYear().substring(0, 4));
        boolean firstSemester = date.getYear() == startYear && date.getMonthValue() >= 9;
        boolean secondSemester = date.getYear() == startYear + 1 && date.getMonthValue() <= 5;
        if (!firstSemester && !secondSemester) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "FINAL_GRADE_OUTSIDE_SEMESTER",
                "Підсумковий тип роботи доступний лише протягом навчального семестру");
        }
        if (workType == LessonWorkType.YEAR && !secondSemester) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "YEAR_GRADE_SECOND_SEMESTER_ONLY",
                "Річну оцінку можна виставити лише в другому семестрі");
        }
        LocalDate start = workType == LessonWorkType.YEAR || secondSemester
            ? LocalDate.of(startYear + 1, 1, 1) : LocalDate.of(startYear, 9, 1);
        LocalDate end = workType == LessonWorkType.YEAR || secondSemester
            ? LocalDate.of(startYear + 1, 5, 31) : LocalDate.of(startYear, 12, 31);
        if (lessonRepository.existsOtherJournalWorkType(
            lesson.getScheduleEntry().getSchoolClass().getId(),
            lesson.getScheduleEntry().getSubject().getId(), workType, start, end,
            lesson.getId(), LessonStatus.CANCELLED
        )) {
            throw new ApplicationException(HttpStatus.CONFLICT,
                workType == LessonWorkType.YEAR ? "YEAR_GRADE_ALREADY_EXISTS" : "SEMESTER_GRADE_ALREADY_EXISTS",
                workType == LessonWorkType.YEAR
                    ? "Річну оцінку з цього предмета вже призначено для класу"
                    : "Семестрову оцінку з цього предмета вже призначено для класу в цьому семестрі");
        }
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException error) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_JOURNAL_MONTH",
                "Період має бути у форматі РРРР-ММ");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String slotKey(Long scheduleId, LocalDate date) {
        return scheduleId + ":" + date;
    }

    private String cellKey(Long lessonId, Long studentId) {
        return lessonId + ":" + studentId;
    }

    private ApplicationException journalNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "JOURNAL_NOT_FOUND", "Журнал не знайдено");
    }

    private record JournalSlot(ScheduleEntry entry, LocalDate date, Lesson lesson) { }
}
