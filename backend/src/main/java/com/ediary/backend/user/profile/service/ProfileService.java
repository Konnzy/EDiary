package com.ediary.backend.user.profile.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.notification.entity.UserNotificationSettings;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.profile.dto.ProfileDetailsResponse;
import com.ediary.backend.user.profile.dto.UpdateNotificationPreferencesRequest;
import com.ediary.backend.user.profile.entity.UserAvatar;
import com.ediary.backend.user.profile.repository.UserAvatarRepository;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository users;
    private final StudentProfileRepository students;
    private final ParentProfileRepository parents;
    private final TeacherProfileRepository teachers;
    private final StudentParentLinkRepository links;
    private final SchoolClassRepository classes;
    private final ScheduleRepository schedules;
    private final UserNotificationSettingsRepository settings;
    private final UserAvatarRepository avatars;
    private final Clock clock;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public ProfileDetailsResponse find(String login) {
        AppUser user = requireUser(login);
        ProfileDetailsResponse.Education education = null;
        ProfileDetailsResponse.Professional professional = null;
        List<ProfileDetailsResponse.Child> children = List.of();
        String firstName;
        String lastName;
        String patronymic;
        String phone = null;

        switch (user.getRole()) {
            case STUDENT -> {
                StudentProfile student = students.findByUser_Id(user.getId()).orElseThrow(this::profileMissing);
                firstName = student.getName();
                lastName = student.getSurname();
                patronymic = student.getPatronymic();
                SchoolClass schoolClass = student.getSchoolClass();
                TeacherProfile classTeacher = schoolClass == null ? null : schoolClass.getClassTeacher();
                ParentProfile parent = links.findAllByStudent_IdOrderByPrimaryContactDesc(student.getId())
                    .stream().map(StudentParentLink::getParent).findFirst().orElse(null);
                education = new ProfileDetailsResponse.Education(
                    schoolClass == null ? null : schoolClass.getDisplayName(),
                    fullName(classTeacher),
                    classTeacher == null ? null : classTeacher.getEmail(),
                    classTeacher == null ? null : classTeacher.getPhone(),
                    parent == null ? null : parent.getEmail(),
                    parent == null ? null : parent.getPhone()
                );
            }
            case PARENT -> {
                ParentProfile parent = parents.findByUser_Id(user.getId()).orElseThrow(this::profileMissing);
                firstName = parent.getName();
                lastName = parent.getSurname();
                patronymic = parent.getPatronymic();
                phone = parent.getPhone();
                children = links.findAllByParentLogin(user.getLogin()).stream().map(link -> {
                    StudentProfile student = link.getStudent();
                    SchoolClass schoolClass = student.getSchoolClass();
                    TeacherProfile classTeacher = schoolClass == null ? null : schoolClass.getClassTeacher();
                    return new ProfileDetailsResponse.Child(
                        student.getId(),
                        fullName(student.getSurname(), student.getName(), student.getPatronymic()),
                        student.getUser().getEmail(),
                        schoolClass == null ? null : schoolClass.getDisplayName(),
                        fullName(classTeacher),
                        classTeacher == null ? null : classTeacher.getEmail(),
                        classTeacher == null ? null : classTeacher.getPhone()
                    );
                }).toList();
            }
            case TEACHER -> {
                TeacherProfile teacher = teachers.findByUser_Id(user.getId()).orElseThrow(this::profileMissing);
                firstName = teacher.getName();
                lastName = teacher.getSurname();
                patronymic = teacher.getPatronymic();
                phone = teacher.getPhone();
                List<String> subjects = teacher.getSubjects().stream().map(subject -> subject.getTitle())
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
                String academicYear = currentAcademicYear();
                List<String> assignedClasses = schedules.findActiveTeacherClassAssignmentsForYear(teacher.getId(), academicYear).stream()
                    .map(assignment -> assignment.getClassNumber() + "-" + assignment.getClassLetter())
                    .distinct().sorted().toList();
                List<String> homeroomClasses = classes.findAllByClassTeacherIdOrderByNumberAscLetterAsc(teacher.getId())
                    .stream().filter(value -> value.getAcademicYear().getValue().equals(academicYear))
                    .map(SchoolClass::getDisplayName).toList();
                professional = new ProfileDetailsResponse.Professional("Вчитель", subjects, assignedClasses, homeroomClasses);
            }
            default -> throw new ApplicationException(HttpStatus.FORBIDDEN, "PROFILE_UNAVAILABLE", "Профіль для цієї ролі недоступний");
        }

        ProfileDetailsResponse.NotificationPreferences preferences = toPreferences(
            settings.findByUser_Id(user.getId()).orElse(null)
        );
        Long avatarVersion = avatars.findUpdatedAtByUserId(user.getId())
            .map(value -> value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).orElse(null);
        return new ProfileDetailsResponse(user.getId(), user.getRole(), firstName, lastName, patronymic,
            user.getLogin(), user.getEmail(), phone, user.getCreatedAt(), avatarVersion,
            education, professional, children, preferences);
    }

    @Transactional
    public ProfileDetailsResponse updatePhone(String login, String phone) {
        AppUser user = requireUser(login);
        String normalized = phone.strip().replaceAll("\\s+", " ");
        if (user.getRole() == UserRole.PARENT) {
            ParentProfile parent = parents.findByUser_Id(user.getId()).orElseThrow(this::profileMissing);
            parent.setPhone(normalized);
            parents.save(parent);
        } else if (user.getRole() == UserRole.TEACHER) {
            TeacherProfile teacher = teachers.findByUser_Id(user.getId()).orElseThrow(this::profileMissing);
            teacher.setPhone(normalized);
            teachers.save(teacher);
        } else {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "PHONE_READ_ONLY", "Номер телефону учня не можна змінити");
        }
        user.setUpdatedAt(LocalDateTime.now(clock));
        audit.record("PROFILE_PHONE_UPDATED", "USER", user.getId(), of(), of("phoneChanged", true));
        return find(login);
    }

    @Transactional
    public ProfileDetailsResponse updateNotifications(String login, UpdateNotificationPreferencesRequest request) {
        AppUser user = requireUser(login);
        UserNotificationSettings preferences = settings.findByUser_Id(user.getId()).orElseGet(() -> {
            UserNotificationSettings created = new UserNotificationSettings();
            created.setUser(user);
            return created;
        });
        if (user.getRole() == UserRole.TEACHER) {
            if (request.newMessage() == null || request.scheduleChange() == null) throw invalidPreferences();
            preferences.setMessageEmailEnabled(request.newMessage());
            preferences.setScheduleChangeEmailEnabled(request.scheduleChange());
            if (isHomeroomTeacher(user)) {
                if (request.classScheduleChange() == null || request.prolongedAbsence() == null
                    || request.classRosterChange() == null) throw invalidPreferences();
                preferences.setClassScheduleChangeEmailEnabled(request.classScheduleChange());
                preferences.setProlongedAbsenceEmailEnabled(request.prolongedAbsence());
                preferences.setClassRosterChangeEmailEnabled(request.classRosterChange());
            }
        } else {
            if (request.newGrade() == null || request.newHomework() == null || request.absence() == null
                || request.scheduleChange() == null || request.newMessage() == null
                || request.homeworkDeadlineReminder() == null) throw invalidPreferences();
            preferences.setGradeEmailEnabled(request.newGrade());
            preferences.setHomeworkEmailEnabled(request.newHomework());
            preferences.setAttendanceEmailEnabled(request.absence());
            preferences.setScheduleChangeEmailEnabled(request.scheduleChange());
            preferences.setMessageEmailEnabled(request.newMessage());
            preferences.setHomeworkDeadlineReminderEmailEnabled(request.homeworkDeadlineReminder());
        }
        settings.save(preferences);
        audit.record("NOTIFICATION_PREFERENCES_UPDATED", "USER", user.getId(), of(), of("notificationPreferencesChanged", true));
        return find(login);
    }

    @Transactional(readOnly = true)
    public UserAvatar findAvatar(String login) {
        AppUser user = requireUser(login);
        return avatars.findById(user.getId()).orElseThrow(() -> new ApplicationException(
            HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "Фото профілю не знайдено"));
    }

    @Transactional
    public ProfileDetailsResponse uploadAvatar(String login, MultipartFile file) {
        AppUser user = requireUser(login);
        byte[] bytes = AvatarImage.normalize(file);
        UserAvatar avatar = avatars.findById(user.getId()).orElseGet(() -> {
            UserAvatar created = new UserAvatar();
            created.setUser(user);
            return created;
        });
        avatar.setContentType(AvatarImage.CONTENT_TYPE);
        avatar.setImageData(bytes);
        avatar.setUpdatedAt(LocalDateTime.now(clock));
        avatars.saveAndFlush(avatar);
        audit.record("PROFILE_AVATAR_UPDATED", "USER", user.getId(), of(), of("avatarChanged", true));
        return find(login);
    }

    @Transactional
    public void deleteAvatar(String login) {
        Long userId = requireUser(login).getId();
        avatars.deleteById(userId);
        audit.record("PROFILE_AVATAR_REMOVED", "USER", userId, of(), of("avatarChanged", true));
    }

    private AppUser requireUser(String login) {
        AppUser user = users.findByLoginIgnoreCase(login).orElseThrow(this::profileMissing);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "Обліковий запис неактивний");
        }
        return user;
    }

    private static ProfileDetailsResponse.NotificationPreferences toPreferences(UserNotificationSettings value) {
        return value == null
            ? new ProfileDetailsResponse.NotificationPreferences(false, false, false, false, false, false, false, false, false)
            : new ProfileDetailsResponse.NotificationPreferences(value.isGradeEmailEnabled(),
                value.isHomeworkEmailEnabled(), value.isAttendanceEmailEnabled(),
                value.isMessageEmailEnabled(), value.isScheduleChangeEmailEnabled(),
                value.isClassScheduleChangeEmailEnabled(), value.isProlongedAbsenceEmailEnabled(),
                value.isClassRosterChangeEmailEnabled(), value.isHomeworkDeadlineReminderEmailEnabled());
    }

    private boolean isHomeroomTeacher(AppUser user) {
        TeacherProfile teacher = teachers.findByUser_Id(user.getId()).orElseThrow(this::profileMissing);
        String academicYear = currentAcademicYear();
        return classes.findAllByClassTeacherIdOrderByNumberAscLetterAsc(teacher.getId()).stream()
            .anyMatch(schoolClass -> schoolClass.getAcademicYear().getValue().equals(academicYear));
    }

    private static String fullName(TeacherProfile teacher) {
        return teacher == null ? null : teacher.getSurname() + " " + teacher.getName()
            + (teacher.getPatronymic() == null ? "" : " " + teacher.getPatronymic());
    }

    private static String fullName(String surname, String name, String patronymic) {
        return surname + " " + name + (patronymic == null || patronymic.isBlank() ? "" : " " + patronymic);
    }

    private String currentAcademicYear() {
        LocalDate today = LocalDate.now(clock);
        int startYear = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    private ApplicationException invalidPreferences() {
        return new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_SETTINGS", "Передайте всі налаштування сповіщень для вашої ролі");
    }

    private ApplicationException profileMissing() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Профіль не знайдено");
    }
}
