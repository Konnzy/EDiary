package com.ediary.backend.user.admin.dashboard;

import com.ediary.backend.user.admin.dashboard.dto.AdminDashboardLessonResponse;
import com.ediary.backend.user.admin.dashboard.dto.AdminActivityResponse;
import com.ediary.backend.user.admin.dashboard.dto.AdminDashboardRequestResponse;
import com.ediary.backend.user.admin.dashboard.dto.AdminDashboardResponse;
import com.ediary.backend.user.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.Weekday;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.service.UserScheduleService;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository userRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final AdminActivityRepository adminActivityRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserScheduleService userScheduleService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardResponse find() {
        LocalDate today = LocalDate.now(clock);
        var scheduleOptions = userScheduleService.findOptions();
        boolean currentYearExists = scheduleOptions.academicYears()
            .contains(scheduleOptions.currentAcademicYear());

        List<AdminDashboardLessonResponse> lessons = currentYearExists
            ? findTodayLessons(today, scheduleOptions.currentAcademicYear(), scheduleOptions.currentWeek())
            : List.of();

        return new AdminDashboardResponse(
            findStats(),
            registrationRequestRepository.findTop4ByOrderByCreatedAtDesc().stream()
                .map(this::toRequestResponse)
                .toList(),
            adminActivityRepository.findTop4ByOrderByOccurredAtDescIdDesc().stream()
                .map(activity -> new AdminActivityResponse(
                    activity.getId(), activity.getActorLogin(), activity.getTitle(),
                    activity.getTarget(), activity.getOccurredAt()
                ))
                .toList(),
            lessons,
            today,
            scheduleOptions.currentAcademicYear(),
            scheduleOptions.currentWeek()
        );
    }

    private AdminDashboardStatsResponse findStats() {
        return new AdminDashboardStatsResponse(
            userRepository.countByRoleAndStatusNot(UserRole.STUDENT, UserStatus.DELETED),
            userRepository.countByRoleAndStatus(UserRole.STUDENT, UserStatus.ACTIVE),
            userRepository.countByRoleAndStatusNot(UserRole.TEACHER, UserStatus.DELETED),
            userRepository.countByRoleAndStatus(UserRole.TEACHER, UserStatus.ACTIVE),
            userRepository.countByRoleAndStatusNot(UserRole.PARENT, UserStatus.DELETED),
            userRepository.countByRoleAndStatus(UserRole.PARENT, UserStatus.ACTIVE),
            userRepository.countByStatus(UserStatus.ACTIVE),
            userRepository.countByStatusNot(UserStatus.DELETED),
            registrationRequestRepository.countByStatus(RegistrationStatus.PENDING)
        );
    }

    private List<AdminDashboardLessonResponse> findTodayLessons(
        LocalDate today,
        String academicYear,
        Integer week
    ) {
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return List.of();
        }
        return scheduleRepository
            .findAllByAcademicYearAndWeekNumberAndWeekdayAndActiveTrueOrderByLessonNumberAscSchoolClass_NumberAscSchoolClass_LetterAsc(
                academicYear,
                week,
                Weekday.valueOf(dayOfWeek.name())
            )
            .stream()
            .limit(6)
            .map(this::toLessonResponse)
            .toList();
    }

    private AdminDashboardRequestResponse toRequestResponse(RegistrationRequest request) {
        return new AdminDashboardRequestResponse(
            request.getId(),
            fullName(request),
            request.getEmail(),
            request.getRequestedRole(),
            requestDetails(request),
            request.getStatus(),
            request.getCreatedAt(),
            request.getReviewedAt()
        );
    }

    private AdminDashboardLessonResponse toLessonResponse(ScheduleEntry entry) {
        return new AdminDashboardLessonResponse(
            entry.getId(),
            entry.getStartTime().format(TIME_FORMAT),
            entry.getSubject().getTitle(),
            entry.getSchoolClass().getDisplayName(),
            fullName(entry.getTeacher()),
            entry.getRoom()
        );
    }

    private String requestDetails(RegistrationRequest request) {
        if (request.getRequestedClass() != null) {
            return request.getRequestedClass().getDisplayName() + " клас";
        }
        if (request.getRequestedSubject() != null) {
            return request.getRequestedSubject().getTitle();
        }
        if (request.getRequestedStudent() != null) {
            return "Дитина: " + request.getRequestedStudent().getName()
                + " " + request.getRequestedStudent().getSurname();
        }
        return "Без додаткових даних";
    }

    private String fullName(RegistrationRequest request) {
        return request.getSurname() + " " + request.getName()
            + optionalPatronymic(request.getPatronymic());
    }

    private String fullName(TeacherProfile teacher) {
        return teacher.getSurname() + " " + teacher.getName()
            + optionalPatronymic(teacher.getPatronymic());
    }

    private String optionalPatronymic(String patronymic) {
        return patronymic == null || patronymic.isBlank() ? "" : " " + patronymic;
    }
}
