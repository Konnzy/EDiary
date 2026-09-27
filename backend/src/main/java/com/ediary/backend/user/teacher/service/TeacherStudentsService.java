package com.ediary.backend.user.teacher.service;

import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.service.SchedulePeriod;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.teacher.dto.TeacherStudentResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherStudentsService {
    private final ScheduleRepository scheduleRepository;
    private final StudentProfileRepository studentRepository;
    private final StudentParentLinkRepository parentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TeacherStudentResponse> find(String login) {
        String year = SchedulePeriod.academicYear(LocalDate.now(clock));
        Map<Long, Map<Long, TeacherStudentResponse.Subject>> subjectsByClass = new LinkedHashMap<>();
        for (var entry : scheduleRepository.findJournalAssignmentsForTeacher(login, year)) {
            if (!entry.isActive()) continue;
            subjectsByClass.computeIfAbsent(entry.getSchoolClass().getId(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(entry.getSubject().getId(), new TeacherStudentResponse.Subject(
                    entry.getSubject().getId(), entry.getSubject().getTitle()));
        }
        if (subjectsByClass.isEmpty()) return List.of();
        var students = studentRepository.findAllBySchoolClassIdInOrderBySurnameAscNameAsc(
            List.copyOf(subjectsByClass.keySet()));
        if (students.isEmpty()) return List.of();
        var parentsByStudent = parentRepository.findAllByStudent_IdInOrderByPrimaryContactDesc(
            students.stream().map(student -> student.getId()).toList())
            .stream().collect(Collectors.groupingBy(link -> link.getStudent().getId()));
        return students.stream().map(student -> new TeacherStudentResponse(
            student.getId(), student.getUser().getId(),
            fullName(student.getSurname(), student.getName(), student.getPatronymic()),
            student.getSchoolClass().getId(), student.getSchoolClass().getDisplayName(),
            subjectsByClass.get(student.getSchoolClass().getId()).values().stream()
                .sorted(Comparator.comparing(TeacherStudentResponse.Subject::name)).toList(),
            parentsByStudent.getOrDefault(student.getId(), List.of()).stream()
                .map(TeacherStudentsService::parentContact).toList()
        )).toList();
    }

    private static TeacherStudentResponse.ParentContact parentContact(StudentParentLink link) {
        var parent = link.getParent();
        return new TeacherStudentResponse.ParentContact(
            fullName(parent.getSurname(), parent.getName(), parent.getPatronymic()),
            parent.getPhone(), parent.getEmail());
    }

    private static String fullName(String surname, String name, String patronymic) {
        return Stream.of(surname, name, patronymic).filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(" "));
    }
}
