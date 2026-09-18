package com.ediary.backend.school.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.dto.response.ScheduleOptionsResponse;
import com.ediary.backend.school.dto.response.UserScheduleLessonResponse;
import com.ediary.backend.school.dto.response.UserScheduleResponse;
import com.ediary.backend.school.entity.AcademicYear;
import com.ediary.backend.school.entity.ScheduleEntry;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.repository.AcademicYearRepository;
import com.ediary.backend.school.repository.ScheduleRepository;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserScheduleService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleRepository scheduleRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentParentLinkRepository studentParentLinkRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScheduleOptionsResponse findOptions() {
        LocalDate today = LocalDate.now(clock);
        String currentAcademicYear = SchedulePeriod.academicYear(today);
        return new ScheduleOptionsResponse(
            academicYearRepository.findAllByOrderByValueAsc().stream()
                .map(AcademicYear::getValue)
                .toList(),
            currentAcademicYear,
            SchedulePeriod.weekNumber(today)
        );
    }

    @Transactional(readOnly = true)
    public UserScheduleResponse findForStudent(String login, Integer week) {
        String academicYear = SchedulePeriod.academicYear(LocalDate.now(clock));
        validatePeriod(academicYear, week);
        StudentProfile student = studentProfileRepository.findByUser_LoginIgnoreCase(login)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "STUDENT_PROFILE_NOT_FOUND",
                "Профіль учня не знайдено"
            ));
        return findForClass(student.getSchoolClass(), academicYear, week);
    }

    @Transactional(readOnly = true)
    public UserScheduleResponse findForParent(
        String parentLogin,
        Long studentId,
        Integer week
    ) {
        String academicYear = SchedulePeriod.academicYear(LocalDate.now(clock));
        validatePeriod(academicYear, week);
        StudentProfile student = studentParentLinkRepository
            .findByParentLoginAndStudentId(parentLogin, studentId)
            .map(link -> link.getStudent())
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "PARENT_CHILD_NOT_FOUND",
                "Дитину не знайдено серед прив’язаних до вашого профілю"
            ));
        return findForClass(student.getSchoolClass(), academicYear, week);
    }

    @Transactional(readOnly = true)
    public UserScheduleResponse findForTeacher(String login, Integer week) {
        String academicYear = SchedulePeriod.academicYear(LocalDate.now(clock));
        validatePeriod(academicYear, week);
        List<ScheduleEntry> entries = scheduleRepository.findAllForTeacher(login, academicYear, week);
        return new UserScheduleResponse(
            academicYear,
            week,
            null,
            entries.stream().map(this::toResponse).toList()
        );
    }

    private UserScheduleResponse findForClass(
        SchoolClass schoolClass,
        String academicYear,
        Integer week
    ) {
        if (schoolClass == null || !schoolClass.getAcademicYear().getValue().equals(academicYear)) {
            return new UserScheduleResponse(academicYear, week, null, List.of());
        }
        List<ScheduleEntry> entries = scheduleRepository
            .findAllBySchoolClass_IdAndAcademicYearAndWeekNumberAndActiveTrueOrderByWeekdayAscLessonNumberAsc(
                schoolClass.getId(),
                academicYear,
                week
            );
        return new UserScheduleResponse(
            academicYear,
            week,
            className(schoolClass),
            entries.stream().map(this::toResponse).toList()
        );
    }

    private UserScheduleLessonResponse toResponse(ScheduleEntry entry) {
        return new UserScheduleLessonResponse(
            entry.getId(),
            entry.getWeekday(),
            entry.getLessonNumber(),
            entry.getSubject().getTitle(),
            teacherName(entry),
            className(entry.getSchoolClass()),
            entry.getRoom(),
            entry.getStartTime().format(TIME_FORMAT),
            entry.getEndTime().format(TIME_FORMAT)
        );
    }

    private String teacherName(ScheduleEntry entry) {
        var teacher = entry.getTeacher();
        return teacher.getSurname() + " " + teacher.getName()
            + (teacher.getPatronymic() == null || teacher.getPatronymic().isBlank()
                ? ""
                : " " + teacher.getPatronymic());
    }

    private String className(SchoolClass schoolClass) {
        return schoolClass.getNumber() + "-" + schoolClass.getLetter();
    }

    private void validatePeriod(String academicYear, Integer week) {
        if (academicYear == null || !academicYear.matches("\\d{4}/\\d{4}")) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ACADEMIC_YEAR",
                "Навчальний рік має бути у форматі РРРР/РРРР"
            );
        }
        int start = Integer.parseInt(academicYear.substring(0, 4));
        int end = Integer.parseInt(academicYear.substring(5));
        if (end != start + 1) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ACADEMIC_YEAR",
                "Навчальний рік має містити два послідовні роки"
            );
        }
        if (week == null || week < 1 || week > 2) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SCHEDULE_WEEK",
                "Номер навчального тижня має бути 1 або 2"
            );
        }
        if (!academicYearRepository.existsById(academicYear)) {
            throw new ApplicationException(
                HttpStatus.NOT_FOUND,
                "ACADEMIC_YEAR_NOT_FOUND",
                "Навчальний рік не знайдено"
            );
        }
    }

}
