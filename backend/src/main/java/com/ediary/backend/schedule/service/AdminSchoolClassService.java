package com.ediary.backend.schedule.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.dto.request.SaveSchoolClassRequest;
import com.ediary.backend.schedule.dto.response.AdminClassOptionsResponse;
import com.ediary.backend.schedule.dto.response.AdminClassStudentResponse;
import com.ediary.backend.schedule.dto.response.AdminSchoolClassResponse;
import com.ediary.backend.schedule.dto.response.TeacherClassResponse;
import com.ediary.backend.schedule.dto.response.AdminStudentParentContactResponse;
import com.ediary.backend.schedule.dto.response.ClassTeacherOptionResponse;
import com.ediary.backend.schedule.dto.response.ClassDeletionEligibilityResponse;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.entity.StudentClassMembership;
import com.ediary.backend.schedule.repository.AcademicYearRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.notification.service.UserNotificationService;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSchoolClassService {

    public static final int CLASS_CAPACITY = 30;
    private static final Locale UKRAINIAN_LOCALE = Locale.forLanguageTag("uk-UA");

    private final SchoolClassRepository schoolClassRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentParentLinkRepository studentParentLinkRepository;
    private final StudentClassMembershipRepository membershipRepository;
    private final AcademicYearRepository academicYearRepository;
    private final TeacherClassService teacherClassService;
    private final UserNotificationService notificationService;
    private final Clock clock;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<AdminSchoolClassResponse> findAll(String query, Integer number, String academicYear) {
        String normalizedQuery = normalize(query);
        String resolvedYear = resolveAcademicYear(academicYear);

        return schoolClassRepository.findAllByAcademicYearValueOrderByNumberAscLetterAsc(resolvedYear).stream()
            .filter(schoolClass -> number == null || schoolClass.getNumber().equals(number))
            .filter(schoolClass -> matchesQuery(schoolClass, normalizedQuery))
            .map(schoolClass -> toResponse(
                schoolClass,
                membershipRepository.countBySchoolClassIdAndActiveTrue(schoolClass.getId())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminClassOptionsResponse getOptions(String academicYear) {
        String resolvedYear = resolveAcademicYear(academicYear);
        Map<Long, SchoolClass> assignments = schoolClassRepository
            .findAllByAcademicYearValueOrderByNumberAscLetterAsc(resolvedYear).stream()
            .filter(schoolClass -> schoolClass.getClassTeacher() != null)
            .collect(Collectors.toMap(schoolClass -> schoolClass.getClassTeacher().getId(), Function.identity()));

        List<ClassTeacherOptionResponse> teachers = teacherProfileRepository
            .findAllByUser_StatusOrderBySurnameAscNameAsc(UserStatus.ACTIVE)
            .stream()
            .map(teacher -> {
                SchoolClass assignedClass = assignments.get(teacher.getId());
                return new ClassTeacherOptionResponse(
                    teacher.getId(),
                    fullName(teacher),
                    assignedClass == null ? null : assignedClass.getId(),
                    assignedClass == null ? null : assignedClass.getDisplayName()
                );
            })
            .toList();
        return new AdminClassOptionsResponse(teachers);
    }

    @Transactional(readOnly = true)
    public AdminSchoolClassResponse findById(Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        return toResponse(schoolClass, membershipRepository.countBySchoolClassIdAndActiveTrue(classId));
    }

    @Transactional(readOnly = true)
    public TeacherClassResponse.Statistics findStatistics(Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        List<Long> studentIds = membershipRepository
            .findAllBySchoolClassIdAndActiveTrueOrderByStudentSurnameAscStudentNameAsc(classId).stream()
            .map(membership -> membership.getStudent().getId()).toList();
        return teacherClassService.statistics(schoolClass, studentIds);
    }

    @Transactional(readOnly = true)
    public List<AdminClassStudentResponse> findStudents(Long classId, String query) {
        requireClass(classId);
        String normalizedQuery = normalize(query);
        return membershipRepository.findAllBySchoolClassIdAndActiveTrueOrderByStudentSurnameAscStudentNameAsc(classId)
            .stream()
            .map(StudentClassMembership::getStudent)
            .filter(student -> matchesStudentQuery(student, normalizedQuery))
            .map(this::toStudentResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminStudentParentContactResponse> findStudentParents(Long classId, Long studentId) {
        requireClass(classId);
        if (membershipRepository.findByStudentIdAndSchoolClassIdAndActiveTrue(studentId, classId).isEmpty()) {
            throw new ApplicationException(HttpStatus.NOT_FOUND, "STUDENT_NOT_IN_CLASS", "Учня не знайдено в цьому класі");
        }
        return studentParentLinkRepository.findAllByStudent_IdOrderByPrimaryContactDesc(studentId).stream()
            .map(link -> {
                ParentProfile parent = link.getParent();
                String fullName = parent.getSurname() + " " + parent.getName()
                    + (parent.getPatronymic() == null || parent.getPatronymic().isBlank()
                        ? "" : " " + parent.getPatronymic());
                return new AdminStudentParentContactResponse(
                    parent.getId(), fullName, parent.getEmail(), parent.getPhone()
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminClassStudentResponse> findUnassignedStudents(String query, String academicYear) {
        String normalizedQuery = normalize(query);
        String resolvedYear = resolveAcademicYear(academicYear);
        return studentProfileRepository
            .findAssignableForAcademicYear(
                resolvedYear,
                UserStatus.ACTIVE,
                StudentStatus.STUDYING
            )
            .stream()
            .filter(student -> matchesStudentQuery(student, normalizedQuery))
            .map(this::toStudentResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ClassDeletionEligibilityResponse getDeletionEligibility(Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        List<String> blockers = findDeletionBlockers(schoolClass);
        return new ClassDeletionEligibilityResponse(blockers.isEmpty(), blockers);
    }

    @Transactional
    public AdminSchoolClassResponse create(SaveSchoolClassRequest input) {
        AcademicYear academicYear = requireEditableAcademicYear(input.academicYear());
        String letter = normalizeRequiredLetter(input.letter());
        ensureUniqueClass(academicYear.getValue(), input.number(), letter, null);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setAcademicYear(academicYear);
        apply(schoolClass, input, letter);
        SchoolClass saved = schoolClassRepository.saveAndFlush(schoolClass);
        audit.record("CLASS_CREATED", "CLASS", saved.getId(), of(), auditClass(saved));
        return toResponse(saved, 0L);
    }

    @Transactional
    public AdminSchoolClassResponse update(Long classId, SaveSchoolClassRequest input) {
        SchoolClass schoolClass = requireClassForUpdate(classId);
        var beforeClass = auditClass(schoolClass);
        ensureEditable(schoolClass);
        String letter = normalizeRequiredLetter(input.letter());
        ensureSameAcademicYear(schoolClass, input.academicYear());
        ensureUniqueClass(schoolClass.getAcademicYear().getValue(), input.number(), letter, classId);
        long studentCount = membershipRepository.countBySchoolClassIdAndActiveTrue(classId);
        if (input.classTeacherId() == null && !schoolClass.isDraft()) {
            List<String> blockers = findClassRelations(schoolClass);
            if (!blockers.isEmpty()) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CLASS_TEACHER_REQUIRED",
                    "Не можна прибрати класного керівника: " + String.join(", ", blockers)
                );
            }
        }
        apply(schoolClass, input, letter);
        SchoolClass saved = schoolClassRepository.saveAndFlush(schoolClass);
        audit.record("CLASS_UPDATED", "CLASS", classId, beforeClass, auditClass(saved));
        return toResponse(saved, studentCount);
    }

    @Transactional
    public void delete(Long classId) {
        SchoolClass schoolClass = requireClassForUpdate(classId);
        ensureEditable(schoolClass);
        List<String> blockers = findDeletionBlockers(schoolClass);
        if (!blockers.isEmpty()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "SCHOOL_CLASS_HAS_RELATIONS",
                "Клас не можна видалити: " + String.join(", ", blockers)
            );
        }
        membershipRepository.deleteBySchoolClassIdAndActiveFalse(classId);
        membershipRepository.flush();
        schoolClassRepository.delete(schoolClass);
        schoolClassRepository.flush();
        audit.record("CLASS_DELETED", "CLASS", classId, auditClass(schoolClass), of("deleted", true));
    }

    private void apply(SchoolClass schoolClass, SaveSchoolClassRequest input, String letter) {
        if (input.classTeacherId() != null) {
            Long currentClassId = schoolClass.getId() == null ? -1L : schoolClass.getId();
            if (schoolClassRepository.existsByAcademicYearValueAndClassTeacherIdAndIdNot(
                schoolClass.getAcademicYear().getValue(),
                input.classTeacherId(),
                currentClassId
            )) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CLASS_TEACHER_ALREADY_ASSIGNED",
                    "Цей учитель уже є класним керівником іншого класу"
                );
            }
            TeacherProfile teacher = teacherProfileRepository.findById(input.classTeacherId())
                .filter(value -> value.getUser().getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(
                    HttpStatus.NOT_FOUND,
                    "ACTIVE_TEACHER_NOT_FOUND",
                    "Активного вчителя не знайдено"
                ));
            schoolClass.setClassTeacher(teacher);
        } else {
            schoolClass.setClassTeacher(null);
        }
        schoolClass.setNumber(input.number());
        schoolClass.setLetter(letter);
        schoolClass.setLimit(CLASS_CAPACITY);
    }

    @Transactional
    public AdminClassStudentResponse addStudent(Long classId, Long studentId) {
        SchoolClass targetClass = requireClassForUpdate(classId);
        ensureEditable(targetClass);
        ensureActive(targetClass);
        ensureHasCapacity(targetClass);
        StudentProfile student = requireStudentForUpdate(studentId);
        if (membershipRepository.findByStudentIdAndSchoolClassAcademicYearValueAndActiveTrue(
            studentId,
            targetClass.getAcademicYear().getValue()
        ).isPresent()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "STUDENT_ALREADY_ASSIGNED",
                "Учень уже належить до класу в цьому навчальному році"
            );
        }
        ensureStudentCanBeAssigned(student);
        createMembership(student, targetClass);
        student.setSchoolClass(targetClass);
        AdminClassStudentResponse response = toStudentResponse(studentProfileRepository.saveAndFlush(student));
        notificationService.forTeacher(targetClass.getClassTeacher(), "CLASS_ROSTER", "Зміна складу класу",
            student.getSurname() + " " + student.getName() + " додано до " + targetClass.getDisplayName() + " класу",
            "teacher/my-class?classId=" + targetClass.getId(), null);
        audit.record("STUDENT_ASSIGNED", "STUDENT", studentId, of("classId", null), of("classId", classId));
        return response;
    }

    @Transactional
    public AdminClassStudentResponse transferStudent(Long sourceClassId, Long studentId, Long targetClassId) {
        if (sourceClassId.equals(targetClassId)) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "SAME_TARGET_CLASS",
                "Оберіть інший клас"
            );
        }
        SchoolClass targetClass = requireClassForUpdate(targetClassId);
        SchoolClass sourceClass = requireClassForUpdate(sourceClassId);
        ensureEditable(sourceClass);
        ensureEditable(targetClass);
        ensureActive(targetClass);
        if (!sourceClass.getAcademicYear().getValue().equals(targetClass.getAcademicYear().getValue())) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CLASS_ACADEMIC_YEAR_MISMATCH",
                "Переведення можливе лише між класами одного навчального року"
            );
        }
        ensureHasCapacity(targetClass);
        StudentProfile student = requireStudentForUpdate(studentId);
        ensureStudentBelongsToClass(student, sourceClassId);
        closeMembership(studentId, sourceClassId);
        createMembership(student, targetClass);
        student.setSchoolClass(targetClass);
        AdminClassStudentResponse response = toStudentResponse(studentProfileRepository.saveAndFlush(student));
        String studentName = student.getSurname() + " " + student.getName();
        notificationService.forTeacher(sourceClass.getClassTeacher(), "CLASS_ROSTER", "Зміна складу класу",
            studentName + " переведено до " + targetClass.getDisplayName() + " класу",
            "teacher/my-class?classId=" + sourceClassId, null);
        notificationService.forTeacher(targetClass.getClassTeacher(), "CLASS_ROSTER", "Зміна складу класу",
            studentName + " переведено з " + sourceClass.getDisplayName() + " класу",
            "teacher/my-class?classId=" + targetClassId, null);
        audit.record("STUDENT_TRANSFERRED", "STUDENT", studentId, of("classId", sourceClassId), of("classId", targetClassId));
        return response;
    }

    @Transactional
    public void removeStudent(Long classId, Long studentId) {
        SchoolClass schoolClass = requireClassForUpdate(classId);
        ensureEditable(schoolClass);
        StudentProfile student = requireStudentForUpdate(studentId);
        ensureStudentBelongsToClass(student, classId);
        closeMembership(studentId, classId);
        student.setSchoolClass(null);
        studentProfileRepository.saveAndFlush(student);
        audit.record("STUDENT_REMOVED_FROM_CLASS", "STUDENT", studentId, of("classId", classId), of("classId", null));
        notificationService.forTeacher(schoolClass.getClassTeacher(), "CLASS_ROSTER", "Зміна складу класу",
            student.getSurname() + " " + student.getName() + " вилучено з " + schoolClass.getDisplayName() + " класу",
            "teacher/my-class?classId=" + classId, null);
    }

    private Map<String, Object> auditClass(SchoolClass value) {
        return of("classNumber", value.getNumber(), "classLetter", value.getLetter(), "academicYear", value.getAcademicYear().getValue(),
            "teacherId", value.getClassTeacher() == null ? null : value.getClassTeacher().getId());
    }

    private void ensureUniqueClass(String academicYear, Integer number, String letter, Long excludedId) {
        boolean exists = excludedId == null
            ? schoolClassRepository.existsByAcademicYearValueAndNumberAndLetterIgnoreCase(
                academicYear, number, letter
            )
            : schoolClassRepository.existsByAcademicYearValueAndNumberAndLetterIgnoreCaseAndIdNot(
                academicYear, number, letter, excludedId
            );
        if (exists) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "SCHOOL_CLASS_ALREADY_EXISTS",
                "Клас із таким номером і літерою вже існує"
            );
        }
    }

    private List<String> findDeletionBlockers(SchoolClass schoolClass) {
        List<String> blockers = new ArrayList<>();
        if (schoolClass.getClassTeacher() != null) {
            blockers.add("призначено класного керівника");
        }
        Long classId = schoolClass.getId();
        if (membershipRepository.countBySchoolClassIdAndActiveTrue(classId) > 0
            || studentProfileRepository.countBySchoolClassId(classId) > 0) {
            blockers.add("до класу зараховано учнів");
        }
        addOperationalRelationBlockers(blockers, classId);
        return blockers;
    }

    private List<String> findClassRelations(SchoolClass schoolClass) {
        List<String> blockers = new ArrayList<>();
        Long classId = schoolClass.getId();
        if (membershipRepository.countBySchoolClassIdAndActiveTrue(classId) > 0
            || studentProfileRepository.countBySchoolClassId(classId) > 0) {
            blockers.add("до класу зараховано учнів");
        }
        addOperationalRelationBlockers(blockers, classId);
        return blockers;
    }

    private void addOperationalRelationBlockers(List<String> blockers, Long classId) {
        if (schoolClassRepository.hasSchedule(classId)) {
            blockers.add("для класу створено розклад");
        }
        if (schoolClassRepository.hasRegistrationRequests(classId)) {
            blockers.add("клас використано в реєстраційних заявках");
        }
    }

    private SchoolClass requireClass(Long classId) {
        return schoolClassRepository.findById(classId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "SCHOOL_CLASS_NOT_FOUND",
                "Клас не знайдено"
            ));
    }

    private SchoolClass requireClassForUpdate(Long classId) {
        return schoolClassRepository.findByIdForUpdate(classId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "SCHOOL_CLASS_NOT_FOUND",
                "Клас не знайдено"
            ));
    }

    private StudentProfile requireStudentForUpdate(Long studentId) {
        return studentProfileRepository.findByIdForUpdate(studentId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "STUDENT_NOT_FOUND",
                "Учня не знайдено"
            ));
    }

    private void ensureHasCapacity(SchoolClass schoolClass) {
        if (membershipRepository.countBySchoolClassIdAndActiveTrue(schoolClass.getId()) >= CLASS_CAPACITY) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "SCHOOL_CLASS_IS_FULL",
                "У класі немає вільних місць"
            );
        }
    }

    private void ensureStudentCanBeAssigned(StudentProfile student) {
        if (student.getUser().getStatus() != UserStatus.ACTIVE || student.getStatus() != StudentStatus.STUDYING) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "STUDENT_NOT_ACTIVE",
                "До класу можна додати лише активного учня зі статусом «Навчається»"
            );
        }
    }

    private void ensureStudentBelongsToClass(StudentProfile student, Long classId) {
        if (student.getSchoolClass() == null || !student.getSchoolClass().getId().equals(classId)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "STUDENT_CLASS_CHANGED",
                "Учень більше не належить до цього класу. Оновіть сторінку"
            );
        }
    }

    private void createMembership(StudentProfile student, SchoolClass schoolClass) {
        StudentClassMembership membership = membershipRepository
            .findByStudentIdAndSchoolClassId(student.getId(), schoolClass.getId())
            .orElseGet(StudentClassMembership::new);
        membership.setStudent(student);
        membership.setSchoolClass(schoolClass);
        membership.setActive(true);
        membership.setJoinedAt(LocalDateTime.now(clock));
        membership.setLeftAt(null);
        membershipRepository.save(membership);
    }

    private void closeMembership(Long studentId, Long classId) {
        StudentClassMembership membership = membershipRepository
            .findByStudentIdAndSchoolClassIdAndActiveTrue(studentId, classId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.CONFLICT,
                "STUDENT_CLASS_HISTORY_CHANGED",
                "Склад класу вже змінився. Оновіть сторінку"
            ));
        membership.setActive(false);
        membership.setLeftAt(LocalDateTime.now(clock));
        membershipRepository.save(membership);
    }

    private AcademicYear requireEditableAcademicYear(String value) {
        String current = currentAcademicYear();
        if (!current.equals(value)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "HISTORICAL_CLASS_READ_ONLY",
                "Створювати та змінювати класи можна лише в поточному навчальному році"
            );
        }
        return academicYearRepository.findById(value)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "ACADEMIC_YEAR_NOT_FOUND",
                "Навчальний рік не знайдено"
            ));
    }

    private void ensureEditable(SchoolClass schoolClass) {
        if (!currentAcademicYear().equals(schoolClass.getAcademicYear().getValue())) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "HISTORICAL_CLASS_READ_ONLY",
                "Дані класів за минулі навчальні роки доступні лише для перегляду"
            );
        }
    }

    private void ensureActive(SchoolClass schoolClass) {
        if (schoolClass.isDraft()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CLASS_TEACHER_REQUIRED",
                "Спочатку призначте класного керівника, щоб зараховувати учнів"
            );
        }
    }

    private void ensureSameAcademicYear(SchoolClass schoolClass, String requestedYear) {
        if (!schoolClass.getAcademicYear().getValue().equals(requestedYear)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CLASS_ACADEMIC_YEAR_IMMUTABLE",
                "Навчальний рік існуючого класу змінити не можна"
            );
        }
    }

    private String resolveAcademicYear(String value) {
        return value == null || value.isBlank() ? currentAcademicYear() : value;
    }

    private String currentAcademicYear() {
        LocalDate today = LocalDate.now(clock);
        int startYear = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    private AdminSchoolClassResponse toResponse(SchoolClass schoolClass, long studentCount) {
        TeacherProfile teacher = schoolClass.getClassTeacher();
        return new AdminSchoolClassResponse(
            schoolClass.getId(),
            schoolClass.getNumber(),
            schoolClass.getLetter(),
            schoolClass.getLimit(),
            teacher == null ? null : teacher.getId(),
            teacher == null ? null : fullName(teacher),
            studentCount,
            schoolClass.getAcademicYear().getValue(),
            currentAcademicYear().equals(schoolClass.getAcademicYear().getValue()),
            currentAcademicYear().equals(schoolClass.getAcademicYear().getValue())
                ? schoolClass.isDraft() ? "DRAFT" : "ACTIVE"
                : "ARCHIVED"
        );
    }

    private AdminClassStudentResponse toStudentResponse(StudentProfile student) {
        return new AdminClassStudentResponse(
            student.getId(),
            studentFullName(student),
            student.getUser().getLogin(),
            student.getUser().getEmail(),
            student.getStatus()
        );
    }

    private boolean matchesStudentQuery(StudentProfile student, String query) {
        if (query == null) return true;
        return (studentFullName(student) + " " + student.getUser().getLogin())
            .toLowerCase(Locale.ROOT)
            .contains(query);
    }

    private String studentFullName(StudentProfile student) {
        return student.getSurname() + " " + student.getName()
            + (student.getPatronymic() == null || student.getPatronymic().isBlank()
                ? "" : " " + student.getPatronymic());
    }

    private boolean matchesQuery(SchoolClass schoolClass, String query) {
        if (query == null) return true;
        String displayName = schoolClass.getDisplayName().toLowerCase(Locale.ROOT);
        String teacherName = schoolClass.getClassTeacher() == null
            ? ""
            : fullName(schoolClass.getClassTeacher()).toLowerCase(Locale.ROOT);
        return displayName.contains(query) || teacherName.contains(query);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredLetter(String value) {
        return value.strip().toUpperCase(UKRAINIAN_LOCALE);
    }

    private String fullName(TeacherProfile teacher) {
        return teacher.getSurname() + " " + teacher.getName()
            + (teacher.getPatronymic() == null || teacher.getPatronymic().isBlank()
                ? ""
                : " " + teacher.getPatronymic());
    }
}
