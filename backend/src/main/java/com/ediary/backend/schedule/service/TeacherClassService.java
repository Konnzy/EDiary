package com.ediary.backend.schedule.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.AttendanceStatus;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.schedule.dto.response.TeacherClassOptionResponse;
import com.ediary.backend.schedule.dto.response.TeacherClassResponse;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.repository.LessonRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.time.LocalDate;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherClassService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentClassMembershipRepository membershipRepository;
    private final StudentParentLinkRepository parentLinkRepository;
    private final LessonRepository lessonRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TeacherClassOptionResponse> findClasses(String teacherLogin) {
        TeacherProfile teacher = requireTeacher(teacherLogin);
        return schoolClassRepository.findAllByClassTeacherIdOrderByNumberAscLetterAsc(teacher.getId()).stream()
            .sorted(Comparator.comparing((SchoolClass item) -> item.getAcademicYear().getValue()).reversed()
                .thenComparing(SchoolClass::getNumber).thenComparing(SchoolClass::getLetter))
            .map(item -> new TeacherClassOptionResponse(
                item.getId(), item.getDisplayName(), item.getAcademicYear().getValue()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public TeacherClassResponse findClass(String teacherLogin, Long classId) {
        SchoolClass schoolClass = requireOwnedClass(teacherLogin, classId);
        boolean currentYear = schoolClass.getAcademicYear().getValue()
            .equals(SchedulePeriod.academicYear(LocalDate.now(clock)));
        List<StudentProfile> students = (currentYear
            ? membershipRepository.findAllBySchoolClassIdAndActiveTrueOrderByStudentSurnameAscStudentNameAsc(classId)
            : membershipRepository.findAllBySchoolClassIdOrderByStudentSurnameAscStudentNameAsc(classId))
            .stream().map(membership -> membership.getStudent()).toList();
        List<Long> studentIds = students.stream().map(StudentProfile::getId).toList();
        Map<Long, List<TeacherClassResponse.ParentContact>> parents = studentIds.isEmpty()
            ? Map.of()
            : parentLinkRepository.findAllByStudent_IdInOrderByPrimaryContactDesc(studentIds).stream()
                .collect(Collectors.groupingBy(link -> link.getStudent().getId(), Collectors.mapping(link -> {
                    ParentProfile parent = link.getParent();
                    return new TeacherClassResponse.ParentContact(
                        parent.getId(), fullName(parent.getSurname(), parent.getName(), parent.getPatronymic()),
                        parent.getEmail(), parent.getPhone()
                    );
                }, Collectors.toList())));
        List<TeacherClassResponse.Student> studentResponses = students.stream()
            .map(student -> new TeacherClassResponse.Student(
                student.getId(), fullName(student.getSurname(), student.getName(), student.getPatronymic()),
                student.getUser().getLogin(), student.getUser().getEmail(), student.getStatus(),
                parents.getOrDefault(student.getId(), List.of())
            )).toList();
        return new TeacherClassResponse(
            schoolClass.getId(), schoolClass.getDisplayName(), schoolClass.getAcademicYear().getValue(),
            fullName(schoolClass.getClassTeacher().getSurname(), schoolClass.getClassTeacher().getName(),
                schoolClass.getClassTeacher().getPatronymic()),
            schoolClass.getLimit(), studentResponses, statistics(schoolClass, studentIds)
        );
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(String teacherLogin, Long classId, Long studentId) {
        SchoolClass schoolClass = requireOwnedClass(teacherLogin, classId);
        boolean currentYear = schoolClass.getAcademicYear().getValue()
            .equals(SchedulePeriod.academicYear(LocalDate.now(clock)));
        return (currentYear
            ? membershipRepository.findByStudentIdAndSchoolClassIdAndActiveTrue(studentId, classId)
            : membershipRepository.findByStudentIdAndSchoolClassId(studentId, classId))
            .map(membership -> membership.getStudent())
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND, "STUDENT_NOT_IN_CLASS",
                "Учня не знайдено в цьому класі"));
    }

    public TeacherClassResponse.Statistics statistics(SchoolClass schoolClass, List<Long> studentIds) {
        if (studentIds.isEmpty()) return new TeacherClassResponse.Statistics(null, 0, 0, null, 0);
        String year = schoolClass.getAcademicYear().getValue();
        int startYear = Integer.parseInt(year.substring(0, 4));
        List<Long> lessonIds = lessonRepository.findClassLessons(schoolClass.getId(),
            LocalDate.of(startYear, 9, 1), LocalDate.of(startYear + 1, 8, 31))
            .stream().map(lesson -> lesson.getId()).toList();
        if (lessonIds.isEmpty()) return new TeacherClassResponse.Statistics(null, 0, 0, null, 0);

        Set<Long> includedStudents = Set.copyOf(studentIds);
        var attendance = attendanceRepository.findAllByLessonIdIn(lessonIds).stream()
            .filter(item -> includedStudents.contains(item.getStudent().getId()))
            .filter(item -> item.getStatus() != AttendanceStatus.EXEMPTED).toList();
        long attended = attendance.stream().filter(item -> item.getStatus() == AttendanceStatus.PRESENT
            || item.getStatus() == AttendanceStatus.LATE).count();
        double gradeSum = 0;
        long gradeCount = 0;
        for (var grade : gradeRepository.findAllByLessonIdIn(lessonIds)) {
            if (!includedStudents.contains(grade.getStudent().getId())) continue;
            gradeSum += grade.getScore();
            gradeCount++;
        }
        Double attendancePercent = attendance.isEmpty() ? null
            : Math.round(attended * 1000.0 / attendance.size()) / 10.0;
        Double averageGrade = gradeCount == 0 ? null : Math.round(gradeSum * 10.0 / gradeCount) / 10.0;
        return new TeacherClassResponse.Statistics(
            attendancePercent, attended, attendance.size(), averageGrade, gradeCount
        );
    }

    public SchoolClass requireOwnedClass(String teacherLogin, Long classId) {
        TeacherProfile teacher = requireTeacher(teacherLogin);
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
            .orElseThrow(this::classNotFound);
        if (schoolClass.getClassTeacher() == null || !schoolClass.getClassTeacher().getId().equals(teacher.getId())) {
            throw classNotFound();
        }
        return schoolClass;
    }

    private TeacherProfile requireTeacher(String teacherLogin) {
        return teacherProfileRepository.findByUser_LoginIgnoreCase(teacherLogin)
            .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND, "TEACHER_PROFILE_NOT_FOUND",
                "Профіль вчителя не знайдено"));
    }

    private ApplicationException classNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "HOMEROOM_CLASS_NOT_FOUND",
            "Клас не знайдено серед закріплених за вами");
    }

    private static String fullName(String surname, String name, String patronymic) {
        return Stream.of(surname, name, patronymic).filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(" "));
    }
}
