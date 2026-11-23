package com.ediary.backend.user.teacher.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.parent.service.ParentAccessService;
import com.ediary.backend.user.profile.entity.UserAvatar;
import com.ediary.backend.user.profile.repository.UserAvatarRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.teacher.dto.StudentTeachersResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentTeachersService {
    private final StudentProfileRepository students;
    private final ScheduleRepository schedules;
    private final ParentAccessService parentAccess;
    private final UserAvatarRepository avatars;

    @Transactional(readOnly = true)
    public StudentTeachersResponse forStudent(String login) {
        return find(requireStudent(login));
    }

    @Transactional(readOnly = true)
    public StudentTeachersResponse forParent(String login, Long studentId) {
        return find(parentAccess.requireAccessibleStudent(login, studentId));
    }

    @Transactional(readOnly = true)
    public UserAvatar homeroomAvatarForStudent(String login, Long teacherId) {
        return homeroomAvatar(requireStudent(login), teacherId);
    }

    @Transactional(readOnly = true)
    public UserAvatar homeroomAvatarForParent(String login, Long studentId, Long teacherId) {
        return homeroomAvatar(parentAccess.requireAccessibleStudent(login, studentId), teacherId);
    }

    private StudentProfile requireStudent(String login) {
        return students.findByUser_LoginIgnoreCase(login).orElseThrow(() -> new ApplicationException(
            HttpStatus.NOT_FOUND, "STUDENT_PROFILE_NOT_FOUND", "Профіль учня не знайдено"));
    }

    private UserAvatar homeroomAvatar(StudentProfile student, Long teacherId) {
        var schoolClass = student.getSchoolClass();
        var teacher = schoolClass == null ? null : schoolClass.getClassTeacher();
        if (teacher != null && teacher.getId().equals(teacherId)) {
            var avatar = avatars.findById(teacher.getUser().getId());
            if (avatar.isPresent()) return avatar.get();
        }
        throw new ApplicationException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "Фото профілю не знайдено");
    }

    private StudentTeachersResponse find(StudentProfile student) {
        var schoolClass = student.getSchoolClass();
        if (schoolClass == null) return new StudentTeachersResponse(null, null, List.of());
        Map<Long, TeacherProfile> teachers = new LinkedHashMap<>();
        Map<Long, Map<Long, StudentTeachersResponse.Subject>> subjects = new LinkedHashMap<>();
        for (var entry : schedules.findAllBySchoolClassIdAndAcademicYearAndActiveTrue(
            schoolClass.getId(), schoolClass.getAcademicYear().getValue())) {
            var teacher = entry.getTeacher();
            teachers.putIfAbsent(teacher.getId(), teacher);
            subjects.computeIfAbsent(teacher.getId(), ignored -> new LinkedHashMap<>()).putIfAbsent(
                entry.getSubject().getId(), new StudentTeachersResponse.Subject(entry.getSubject().getId(), entry.getSubject().getTitle()));
        }
        var homeroom = schoolClass.getClassTeacher();
        var homeroomResponse = homeroom == null ? null : response(homeroom, subjects.get(homeroom.getId()),
            avatars.existsById(homeroom.getUser().getId()));
        var items = teachers.values().stream()
            .filter(teacher -> homeroom == null || !teacher.getId().equals(homeroom.getId()))
            .map(teacher -> response(teacher, subjects.get(teacher.getId()), false))
            .sorted(Comparator.comparing(StudentTeachersResponse.Teacher::fullName)).toList();
        return new StudentTeachersResponse(schoolClass.getDisplayName(), homeroomResponse, items);
    }

    private StudentTeachersResponse.Teacher response(
        TeacherProfile teacher, Map<Long, StudentTeachersResponse.Subject> subjects, boolean hasAvatar
    ) {
        String name = Stream.of(teacher.getSurname(), teacher.getName(), teacher.getPatronymic())
            .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" "));
        var sortedSubjects = subjects == null ? List.<StudentTeachersResponse.Subject>of()
            : subjects.values().stream().sorted(Comparator.comparing(StudentTeachersResponse.Subject::name)).toList();
        return new StudentTeachersResponse.Teacher(teacher.getId(), teacher.getUser().getId(), name, teacher.getEmail(),
            teacher.getPhone(), sortedSubjects, hasAvatar);
    }
}
