package com.ediary.backend.journal.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.dto.SubjectProgressResponse;
import com.ediary.backend.journal.entity.Attendance;
import com.ediary.backend.journal.entity.Grade;
import com.ediary.backend.journal.entity.Homework;
import com.ediary.backend.journal.entity.HomeworkStatus;
import com.ediary.backend.journal.entity.StudentHomework;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.journal.repository.HomeworkRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.repository.AcademicYearRepository;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.schedule.service.SchedulePeriod;
import com.ediary.backend.schedule.service.TeacherClassService;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.parent.service.ParentAccessService;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubjectProgressService {

    private final StudentProfileRepository studentProfileRepository;
    private final ParentAccessService parentAccessService;
    private final TeacherClassService teacherClassService;
    private final StudentClassMembershipRepository membershipRepository;
    private final ScheduleRepository scheduleRepository;
    private final LessonRepository lessonRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HomeworkRepository homeworkRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final Clock clock;
    private final AssessmentPolicyService assessmentPolicyService;

    @Transactional(readOnly = true)
    public SubjectProgressResponse forStudent(String login, String academicYear) {
        StudentProfile student = studentProfileRepository.findByUser_LoginIgnoreCase(login)
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND",
                "Профіль учня не знайдено"));
        return find(student, academicYear);
    }

    @Transactional(readOnly = true)
    public SubjectProgressResponse forParent(String login, Long studentId, String academicYear) {
        return find(parentAccessService.requireAccessibleStudent(login, studentId), academicYear);
    }

    @Transactional(readOnly = true)
    public SubjectProgressResponse forAdmin(Long studentId, String academicYear) {
        StudentProfile student = studentProfileRepository.findById(studentId)
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND",
                "Профіль учня не знайдено"));
        return find(student, academicYear);
    }

    @Transactional(readOnly = true)
    public SubjectProgressResponse forHomeroomTeacher(
        String teacherLogin, Long classId, Long studentId, String academicYear
    ) {
        return find(teacherClassService.requireStudent(teacherLogin, classId, studentId), academicYear);
    }

    private SubjectProgressResponse find(StudentProfile student, String requestedYear) {
        String currentYear = SchedulePeriod.academicYear(LocalDate.now(clock));
        String year = requestedYear == null ? currentYear : requestedYear;
        List<String> academicYears = academicYearRepository.findAllByOrderByValueAsc().stream()
            .map(item -> item.getValue()).toList();
        if (!academicYearRepository.existsById(year)) {
            if (year.equals(currentYear)) {
                return new SubjectProgressResponse(academicYears, currentYear, List.of());
            }
            throw new ApplicationException(HttpStatus.NOT_FOUND, "ACADEMIC_YEAR_NOT_FOUND", "Навчальний рік не знайдено");
        }
        Set<Long> classIds = new LinkedHashSet<>();
        membershipRepository.findAllByStudentIdAndSchoolClassAcademicYearValue(student.getId(), year)
            .forEach(membership -> classIds.add(membership.getSchoolClass().getId()));
        if (student.getSchoolClass() != null
            && student.getSchoolClass().getAcademicYear().getValue().equals(year)) {
            classIds.add(student.getSchoolClass().getId());
        }

        Map<Long, SubjectAccumulator> subjects = new LinkedHashMap<>();
        List<Lesson> lessons = new ArrayList<>();
        int startYear = Integer.parseInt(year.substring(0, 4));
        LocalDate start = LocalDate.of(startYear, 9, 1);
        LocalDate end = LocalDate.of(startYear + 1, 8, 31);
        for (Long classId : classIds) {
            for (ScheduleEntry entry : scheduleRepository.findAllBySchoolClassIdAndAcademicYearAndActiveTrue(classId, year)) {
                subjects.computeIfAbsent(entry.getSubject().getId(), ignored -> new SubjectAccumulator(
                    entry.getSubject().getId(), entry.getSubject().getTitle(), teacherName(entry),
                    assessmentPolicyService.find(entry.getSubject().getId())
                ));
            }
            lessons.addAll(lessonRepository.findClassLessons(classId, start, end));
        }
        for (Lesson lesson : lessons) {
            ScheduleEntry entry = lesson.getScheduleEntry();
            subjects.computeIfAbsent(entry.getSubject().getId(), ignored -> new SubjectAccumulator(
                entry.getSubject().getId(), entry.getSubject().getTitle(), teacherName(entry),
                assessmentPolicyService.find(entry.getSubject().getId())
            ));
        }

        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        if (!lessonIds.isEmpty()) {
            Map<Long, Lesson> lessonsById = lessons.stream()
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));
            gradeRepository.findAllByStudentIdAndLessonIdIn(student.getId(), lessonIds).forEach(grade -> {
                Lesson lesson = lessonsById.get(grade.getLesson().getId());
                subjects.get(lesson.getScheduleEntry().getSubject().getId()).grades.add(
                    new SubjectProgressResponse.Grade(
                        grade.getId(), lesson.getDate(), lesson.getScheduleEntry().getLessonNumber(),
                        lesson.getWorkType(), grade.getScore(), grade.getComment(), grade.getResult()
                    )
                );
            });
            attendanceRepository.findAllByStudentIdAndLessonIdIn(student.getId(), lessonIds).forEach(attendance -> {
                Lesson lesson = lessonsById.get(attendance.getLesson().getId());
                subjects.get(lesson.getScheduleEntry().getSubject().getId()).attendance.add(
                    new SubjectProgressResponse.Attendance(
                        attendance.getId(), lesson.getDate(), lesson.getTopic(), attendance.getStatus()
                    )
                );
            });
            List<Homework> homework = homeworkRepository.findAllByLessonIdIn(lessonIds);
            List<Long> homeworkIds = homework.stream().map(Homework::getId).toList();
            Map<Long, StudentHomework> homeworkById = homeworkIds.isEmpty() ? Map.of()
                : studentHomeworkRepository.findAllByStudentIdAndHomeworkIdIn(student.getId(), homeworkIds)
                    .stream().collect(Collectors.toMap(item -> item.getHomework().getId(), item -> item));
            for (Homework assignment : homework) {
                Lesson lesson = lessonsById.get(assignment.getLesson().getId());
                StudentHomework studentHomework = homeworkById.get(assignment.getId());
                HomeworkStatus status = studentHomework == null
                    ? assignment.getDeadline() != null && assignment.getDeadline().isBefore(LocalDate.now(clock))
                        ? HomeworkStatus.OVERDUE : HomeworkStatus.ASSIGNED
                    : studentHomework.getStatus();
                subjects.get(lesson.getScheduleEntry().getSubject().getId()).homework.add(
                    new SubjectProgressResponse.Homework(
                        assignment.getId(), lesson.getDate(), assignment.getDeadline(),
                        assignment.getDescription(), status
                    )
                );
            }
        }

        List<SubjectProgressResponse.Subject> results = subjects.values().stream()
            .sorted(Comparator.comparing(subject -> subject.name))
            .map(subject -> new SubjectProgressResponse.Subject(
                subject.id, subject.name, subject.teacher,
                subject.grades.stream().sorted(Comparator.comparing(SubjectProgressResponse.Grade::date).reversed()).toList(),
                subject.attendance.stream().sorted(Comparator.comparing(SubjectProgressResponse.Attendance::date).reversed()).toList(),
                subject.homework.stream().sorted(Comparator.comparing(SubjectProgressResponse.Homework::assignedDate).reversed()).toList(),
                subject.policy
            ))
            .toList();
        return new SubjectProgressResponse(
            academicYears,
            currentYear, results
        );
    }

    private String teacherName(ScheduleEntry entry) {
        var teacher = entry.getTeacher();
        return teacher.getSurname() + " " + teacher.getName()
            + (teacher.getPatronymic() == null || teacher.getPatronymic().isBlank()
                ? "" : " " + teacher.getPatronymic());
    }

    private static class SubjectAccumulator {
        private final Long id;
        private final String name;
        private final String teacher;
        private final AssessmentPolicyService.Policy policy;
        private final List<SubjectProgressResponse.Grade> grades = new ArrayList<>();
        private final List<SubjectProgressResponse.Attendance> attendance = new ArrayList<>();
        private final List<SubjectProgressResponse.Homework> homework = new ArrayList<>();

        private SubjectAccumulator(Long id, String name, String teacher, AssessmentPolicyService.Policy policy) {
            this.id = id;
            this.name = name;
            this.teacher = teacher;
            this.policy = policy;
        }
    }
}
