package com.ediary.backend.schedule.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.dto.request.SaveSubjectRequest;
import com.ediary.backend.schedule.dto.response.AdminSubjectOptionsResponse;
import com.ediary.backend.schedule.dto.response.AdminSubjectResponse;
import com.ediary.backend.schedule.dto.response.SubjectDeletionEligibilityResponse;
import com.ediary.backend.schedule.dto.response.SubjectTeacherResponse;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSubjectService {

    private final SubjectRepository subjectRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final ScheduleRepository scheduleRepository;
    private final AuditService audit;
    private final com.ediary.backend.journal.service.AssessmentPolicyService assessmentPolicyService;

    @Transactional(readOnly = true)
    public List<AdminSubjectResponse> findAll(String query) {
        String normalizedQuery = normalizeQuery(query);
        Map<Long, List<TeacherProfile>> teachersBySubject = teacherProfileRepository
            .findAllByOrderBySurnameAscNameAsc()
            .stream()
            .flatMap(teacher -> teacher.getSubjects().stream()
                .map(subject -> Map.entry(subject.getId(), teacher)))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));
        return subjectRepository.findAllByOrderByTitleAsc().stream()
            .filter(subject -> normalizedQuery == null
                || subject.getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .map(subject -> toResponse(subject, teachersBySubject.getOrDefault(subject.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminSubjectOptionsResponse getOptions() {
        Map<Long, List<String>> classesByTeacher = scheduleRepository
            .findActiveTeacherClassAssignments()
            .stream()
            .collect(Collectors.groupingBy(
                ScheduleRepository.TeacherClassAssignment::getTeacherId,
                Collectors.mapping(
                    assignment -> assignment.getClassNumber() + "-" + assignment.getClassLetter(),
                    Collectors.toList()
                )
            ));
        List<SubjectTeacherResponse> teachers = teacherProfileRepository
            .findAllByOrderBySurnameAscNameAsc()
            .stream()
            .map(teacher -> toTeacherResponse(
                teacher,
                classesByTeacher.getOrDefault(teacher.getId(), List.of())
            ))
            .toList();
        return new AdminSubjectOptionsResponse(teachers);
    }

    @Transactional
    public AdminSubjectResponse create(SaveSubjectRequest input) {
        String title = normalizeTitle(input.title());
        ensureUniqueTitle(title, null);

        Subject subject = new Subject();
        subject.setTitle(title);
        subject = subjectRepository.saveAndFlush(subject);
        assessmentPolicyService.save(subject.getId(),input.assessmentPolicy()==null
            ? new com.ediary.backend.journal.service.AssessmentPolicyService.Policy(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.NUMERIC,false,false)
            : input.assessmentPolicy());
        List<TeacherProfile> assignedTeachers = synchronizeTeachers(subject, input.teacherIds());
        audit.record("SUBJECT_CREATED", "SUBJECT", subject.getId(), of(),
            of("title", subject.getTitle(), "teacherIds", input.teacherIds()));
        return toResponse(subject, assignedTeachers);
    }

    @Transactional
    public AdminSubjectResponse update(Long subjectId, SaveSubjectRequest input) {
        Subject subject = requireSubjectForUpdate(subjectId);
        if(input.assessmentPolicy()!=null)assessmentPolicyService.save(subjectId,input.assessmentPolicy());
        var beforeSubject = of("title", subject.getTitle(), "teacherIds",
            teacherProfileRepository.findAllAssignedToSubject(subjectId).stream().map(TeacherProfile::getId).sorted().toList());
        String title = normalizeTitle(input.title());
        ensureUniqueTitle(title, subjectId);
        subject.setTitle(title);

        List<TeacherProfile> assignedTeachers = synchronizeTeachers(subject, input.teacherIds());
        subjectRepository.saveAndFlush(subject);
        audit.record("SUBJECT_UPDATED", "SUBJECT", subjectId, beforeSubject,
            of("title", subject.getTitle(), "teacherIds", input.teacherIds().stream().sorted().toList()));
        return toResponse(subject, assignedTeachers);
    }

    @Transactional(readOnly = true)
    public SubjectDeletionEligibilityResponse getDeletionEligibility(Long subjectId) {
        Subject subject = requireSubject(subjectId);
        List<String> blockers = findDeletionBlockers(subject.getId());
        return new SubjectDeletionEligibilityResponse(blockers.isEmpty(), blockers);
    }

    @Transactional
    public void delete(Long subjectId) {
        Subject subject = requireSubjectForUpdate(subjectId);
        List<String> blockers = findDeletionBlockers(subjectId);
        if (!blockers.isEmpty()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "SUBJECT_HAS_RELATIONS",
                "Предмет не можна видалити: " + String.join(", ", blockers)
            );
        }
        subjectRepository.delete(subject);
        subjectRepository.flush();
        audit.record("SUBJECT_DELETED", "SUBJECT", subjectId, of("title", subject.getTitle()), of("deleted", true));
    }

    private List<TeacherProfile> synchronizeTeachers(Subject subject, Set<Long> requestedTeacherIds) {
        Set<Long> teacherIds = new HashSet<>(requestedTeacherIds);
        List<TeacherProfile> currentTeachers = subject.getId() == null
            ? List.of()
            : teacherProfileRepository.findAllAssignedToSubject(subject.getId());
        Set<Long> currentTeacherIds = currentTeachers.stream()
            .map(TeacherProfile::getId)
            .collect(Collectors.toSet());

        List<TeacherProfile> requestedTeachers = teacherIds.isEmpty()
            ? List.of()
            : teacherProfileRepository.findAllById(teacherIds);
        if (requestedTeachers.size() != teacherIds.size()) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "TEACHER_NOT_FOUND",
                "Одного або кількох учителів не знайдено"
            );
        }

        for (TeacherProfile teacher : requestedTeachers) {
            if (!currentTeacherIds.contains(teacher.getId()) && teacher.getUser().getStatus() != UserStatus.ACTIVE) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "TEACHER_NOT_ACTIVE",
                    "До предмета можна призначити лише активного вчителя"
                );
            }
        }

        List<TeacherProfile> changedTeachers = new ArrayList<>();
        for (TeacherProfile teacher : currentTeachers) {
            if (!teacherIds.contains(teacher.getId())) {
                if (subjectRepository.isTeacherAssignmentUsedInSchedule(subject.getId(), teacher.getId())) {
                    throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "TEACHER_SUBJECT_USED_IN_SCHEDULE",
                        "Не можна зняти призначення вчителя, доки воно використовується в розкладі"
                    );
                }
                teacher.getSubjects().remove(subject);
                changedTeachers.add(teacher);
            }
        }
        for (TeacherProfile teacher : requestedTeachers) {
            if (!currentTeacherIds.contains(teacher.getId())) {
                teacher.getSubjects().add(subject);
                changedTeachers.add(teacher);
            }
        }
        if (!changedTeachers.isEmpty()) {
            teacherProfileRepository.saveAll(changedTeachers);
            teacherProfileRepository.flush();
        }
        return requestedTeachers.stream()
            .sorted((left, right) -> fullName(left).compareToIgnoreCase(fullName(right)))
            .toList();
    }

    private List<String> findDeletionBlockers(Long subjectId) {
        List<String> blockers = new ArrayList<>();
        if (subjectRepository.hasTeachers(subjectId)) {
            blockers.add("до предмета прив'язані вчителі");
        }
        if (subjectRepository.hasSchedule(subjectId)) {
            blockers.add("предмет використовується в розкладі");
        }
        if (subjectRepository.hasRegistrationRequests(subjectId)) {
            blockers.add("предмет використано в реєстраційних заявках");
        }
        return blockers;
    }

    private void ensureUniqueTitle(String title, Long excludedId) {
        boolean exists = excludedId == null
            ? subjectRepository.existsByTitleIgnoreCase(title)
            : subjectRepository.existsByTitleIgnoreCaseAndIdNot(title, excludedId);
        if (exists) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "SUBJECT_ALREADY_EXISTS",
                "Предмет із такою назвою вже існує"
            );
        }
    }

    private Subject requireSubject(Long subjectId) {
        return subjectRepository.findById(subjectId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "SUBJECT_NOT_FOUND",
                "Предмет не знайдено"
            ));
    }

    private Subject requireSubjectForUpdate(Long subjectId) {
        return subjectRepository.findByIdForUpdate(subjectId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "SUBJECT_NOT_FOUND",
                "Предмет не знайдено"
            ));
    }

    private AdminSubjectResponse toResponse(Subject subject, List<TeacherProfile> teachers) {
        return new AdminSubjectResponse(
            subject.getId(),
            subject.getTitle(),
            teachers.stream().map(this::toTeacherResponse).toList(),
            assessmentPolicyService.find(subject.getId()), assessmentPolicyService.hasResults(subject.getId())
        );
    }

    private SubjectTeacherResponse toTeacherResponse(TeacherProfile teacher) {
        return toTeacherResponse(teacher, List.of());
    }

    private SubjectTeacherResponse toTeacherResponse(
        TeacherProfile teacher,
        List<String> classNames
    ) {
        List<Subject> subjects = teacher.getSubjects().stream()
            .sorted((left, right) -> left.getTitle().compareToIgnoreCase(right.getTitle()))
            .toList();
        return new SubjectTeacherResponse(
            teacher.getId(),
            fullName(teacher),
            teacher.getUser().getStatus(),
            subjects.stream().map(Subject::getId).toList(),
            subjects.stream().map(Subject::getTitle).toList(),
            classNames
        );
    }

    private String fullName(TeacherProfile teacher) {
        return teacher.getSurname() + " " + teacher.getName()
            + (teacher.getPatronymic() == null || teacher.getPatronymic().isBlank()
                ? ""
                : " " + teacher.getPatronymic());
    }

    private String normalizeTitle(String title) {
        return title.strip().replaceAll("\\s+", " ");
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.strip().toLowerCase(Locale.ROOT);
    }
}
