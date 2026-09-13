package com.ediary.backend.user.service;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.Subject;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.school.repository.SubjectRepository;
import com.ediary.backend.user.dto.request.CreateAdminUserRequest;
import com.ediary.backend.user.dto.response.AdminUserCreationResponse;
import com.ediary.backend.user.dto.response.AdminUserResponse;
import com.ediary.backend.user.dto.response.UserDeletionEligibilityResponse;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.event.AdminUserCreatedEvent;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final StudentParentLinkRepository studentParentLinkRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final ParentLinkCodeService parentLinkCodeService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;

    @Transactional
    public AdminUserCreationResponse create(CreateAdminUserRequest input) {
        if (input.role() == UserRole.ADMIN) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_CREATION_REQUIRES_SEPARATE_FLOW",
                "Створення адміністратора потребує окремого захищеного процесу"
            );
        }

        String login = normalizeLogin(input.login());
        String email = normalizeEmail(input.email());
        ensureUniqueCredentials(login, email);
        validateBirthDate(input.birthDate(), input.role());

        SchoolClass schoolClass = resolveClass(input);
        Subject subject = resolveSubject(input);
        StudentProfile requestedStudent = resolveStudent(input);
        String temporaryPassword = temporaryPasswordGenerator.generate();
        LocalDateTime now = LocalDateTime.now(clock);

        AppUser user = new AppUser();
        user.setLogin(login);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setRole(input.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.saveAndFlush(user);

        String parentLinkCode = createRoleProfile(input, user, schoolClass, subject, requestedStudent, now);
        eventPublisher.publishEvent(new AdminUserCreatedEvent(
            email,
            normalizeName(input.firstName()),
            login,
            temporaryPassword,
            parentLinkCode
        ));

        return new AdminUserCreationResponse(
            toResponse(user),
            "Користувача створено. Лист із даними для входу передано на надсилання."
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> findAll(
        String query,
        UserRole role,
        UserStatus status,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Specification<AppUser> specification = (root, criteriaQuery, builder) -> builder.conjunction();
        String normalizedQuery = normalizeQuery(query);

        if (normalizedQuery != null) {
            String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) -> builder.or(
                builder.like(builder.lower(root.get("login")), pattern),
                builder.like(builder.lower(root.get("email")), pattern)
            ));
        }
        if (role != null) {
            specification = specification.and(
                (root, criteriaQuery, builder) -> builder.equal(root.get("role"), role)
            );
        }
        if (status != null) {
            specification = specification.and(
                (root, criteriaQuery, builder) -> builder.equal(root.get("status"), status)
            );
        }

        Page<AppUser> users = userRepository.findAll(specification, pageable);
        return PageResponse.from(users.map(this::toResponse));
    }

    @Transactional
    public AdminUserResponse changeStatus(Long userId, UserStatus nextStatus, String actorLogin) {
        if (nextStatus == UserStatus.DELETED) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_USER_STATUS_TRANSITION",
                "Для видалення користувача використовуйте окрему дію «Видалити»"
            );
        }
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Користувача не знайдено"
            ));

        if (user.getLogin().equalsIgnoreCase(actorLogin) && nextStatus != UserStatus.ACTIVE) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CANNOT_DISABLE_CURRENT_ADMIN",
                "Не можна заблокувати або деактивувати власний обліковий запис"
            );
        }
        if (user.getRole() == UserRole.ADMIN
            && user.getStatus() == UserStatus.ACTIVE
            && nextStatus != UserStatus.ACTIVE
            && userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "LAST_ACTIVE_ADMIN_REQUIRED",
                "У системі має залишитися щонайменше один активний адміністратор"
            );
        }

        if (user.getStatus() != nextStatus) {
            user.setStatus(nextStatus);
            user.setUpdatedAt(LocalDateTime.now(clock));
            userRepository.saveAndFlush(user);
        }
        if (nextStatus == UserStatus.BLOCKED) {
            expireSessions(user.getLogin());
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserDeletionEligibilityResponse getDeletionEligibility(Long userId) {
        AppUser user = requireUser(userId);
        String reason = findDeletionBlocker(user);
        return new UserDeletionEligibilityResponse(reason == null, reason);
    }

    @Transactional
    public void delete(Long userId, String actorLogin) {
        AppUser user = requireUser(userId);
        if (user.getLogin().equalsIgnoreCase(actorLogin)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CANNOT_DELETE_CURRENT_ADMIN",
                "Не можна видалити власний обліковий запис"
            );
        }

        String blocker = findDeletionBlocker(user);
        if (blocker != null) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "USER_HAS_RELATED_DATA",
                blocker
            );
        }

        expireSessions(user.getLogin());
        deleteRegistrationData(userId);
        deleteTechnicalData(userId);
        jdbcTemplate.update("DELETE FROM parent WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM teacher WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM student WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);
    }

    private AppUser requireUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Користувача не знайдено"
            ));
    }

    private String findDeletionBlocker(AppUser user) {
        Long userId = user.getId();
        if (user.getRole() == UserRole.ADMIN) {
            return "Облікові записи адміністраторів не можна видаляти через розділ користувачів";
        }
        if (exists(
            "SELECT EXISTS (SELECT 1 FROM message WHERE sender_id = ? OR receiver_id = ?)",
            userId,
            userId
        )) {
            return "Користувач має історію повідомлень. Обліковий запис можна лише заблокувати";
        }
        return switch (user.getRole()) {
            case STUDENT -> findStudentDeletionBlocker(userId);
            case TEACHER -> findTeacherDeletionBlocker(userId);
            case PARENT -> findParentDeletionBlocker(userId);
            case ADMIN -> throw new IllegalStateException("Administrator deletion must be blocked");
        };
    }

    private String findStudentDeletionBlocker(Long userId) {
        if (exists("SELECT EXISTS (SELECT 1 FROM mark m JOIN student s ON s.student_id = m.student_id WHERE s.user_id = ?)", userId)) {
            return "Учень має оцінки. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM attendance a JOIN student s ON s.student_id = a.student_id WHERE s.user_id = ?)", userId)) {
            return "Учень має історію відвідування. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM student_homework sh JOIN student s ON s.student_id = sh.student_id WHERE s.user_id = ?)", userId)) {
            return "Учень має історію домашніх завдань. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM student_parents sp JOIN student s ON s.student_id = sp.student_id WHERE s.user_id = ?)", userId)) {
            return "Учень пов’язаний з обліковим записом батьків. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM student_class_membership scm JOIN student s ON s.student_id = scm.student_id WHERE s.user_id = ?)", userId)) {
            return "Учень має історію навчання в класі. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM student WHERE user_id = ? AND class_id IS NOT NULL)", userId)) {
            return "Учень належить до класу. Обліковий запис можна лише заблокувати";
        }
        return null;
    }

    private String findTeacherDeletionBlocker(Long userId) {
        if (exists("SELECT EXISTS (SELECT 1 FROM schedule sc JOIN teacher t ON t.teacher_id = sc.teacher_id WHERE t.user_id = ?)", userId)) {
            return "Учитель має заняття в розкладі. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM school_class c JOIN teacher t ON t.teacher_id = c.class_teacher_id WHERE t.user_id = ?)", userId)) {
            return "Учитель призначений класним керівником. Обліковий запис можна лише заблокувати";
        }
        if (exists("SELECT EXISTS (SELECT 1 FROM teacher_subject ts JOIN teacher t ON t.teacher_id = ts.teacher_id WHERE t.user_id = ?)", userId)) {
            return "Учитель призначений на предмет. Обліковий запис можна лише заблокувати";
        }
        return null;
    }

    private String findParentDeletionBlocker(Long userId) {
        if (exists("SELECT EXISTS (SELECT 1 FROM student_parents sp JOIN parent p ON p.parent_id = sp.parent_id WHERE p.user_id = ?)", userId)) {
            return "Обліковий запис батьків пов’язаний з учнем. Його можна лише заблокувати";
        }
        return null;
    }

    private boolean exists(String sql, Object... arguments) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, arguments));
    }

    private void deleteRegistrationData(Long userId) {
        jdbcTemplate.update(
            "DELETE FROM registration_request WHERE requested_student_id IN (SELECT student_id FROM student WHERE user_id = ?)",
            userId
        );
        jdbcTemplate.update(
            "DELETE FROM registration_request WHERE request_processed_user_id = ?",
            userId
        );
    }

    private void deleteTechnicalData(Long userId) {
        jdbcTemplate.update("DELETE FROM password_reset_token WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_session WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_notification_settings WHERE user_id = ?", userId);
    }

    private void expireSessions(String login) {
        sessionRegistry.getAllSessions(login, false).forEach(session -> session.expireNow());
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.strip();
    }

    private void ensureUniqueCredentials(String login, String email) {
        if (userRepository.existsByLoginIgnoreCase(login)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "LOGIN_ALREADY_EXISTS",
                "Цей логін уже використовується. Введіть інший логін"
            );
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                "Для цієї електронної пошти вже існує обліковий запис"
            );
        }
    }

    private SchoolClass resolveClass(CreateAdminUserRequest input) {
        if (input.role() == UserRole.STUDENT) {
            if (input.classId() == null || input.subjectId() != null) {
                throw invalidRoleDetails();
            }
            SchoolClass schoolClass = schoolClassRepository.findById(input.classId())
                .orElseThrow(() -> new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "CLASS_NOT_FOUND",
                    "Обраний клас не знайдено"
                ));
            LocalDate today = LocalDate.now(clock);
            int startYear = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
            String currentAcademicYear = startYear + "/" + (startYear + 1);
            if (!currentAcademicYear.equals(schoolClass.getAcademicYear().getValue())) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CLASS_ACADEMIC_YEAR_MISMATCH",
                    "Обраний клас не належить до поточного навчального року"
                );
            }
            return schoolClass;
        }
        if (input.classId() != null) {
            throw invalidRoleDetails();
        }
        return null;
    }

    private Subject resolveSubject(CreateAdminUserRequest input) {
        if (input.role() == UserRole.TEACHER) {
            if (input.subjectId() == null || input.classId() != null) {
                throw invalidRoleDetails();
            }
            return subjectRepository.findById(input.subjectId())
                .orElseThrow(() -> new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "SUBJECT_NOT_FOUND",
                    "Обраний предмет не знайдено"
                ));
        }
        if (input.subjectId() != null) {
            throw invalidRoleDetails();
        }
        return null;
    }

    private StudentProfile resolveStudent(CreateAdminUserRequest input) {
        if (input.role() == UserRole.PARENT) {
            return parentLinkCodeService.requireActiveStudent(input.parentLinkCode());
        }
        if (input.parentLinkCode() != null && !input.parentLinkCode().isBlank()) {
            throw invalidRoleDetails();
        }
        return null;
    }

    private String createRoleProfile(
        CreateAdminUserRequest input,
        AppUser user,
        SchoolClass schoolClass,
        Subject subject,
        StudentProfile requestedStudent,
        LocalDateTime now
    ) {
        String firstName = normalizeName(input.firstName());
        String lastName = normalizeName(input.lastName());
        String patronymic = normalizeOptionalName(input.patronymic());

        return switch (input.role()) {
            case STUDENT -> {
                ParentLinkCodeService.GeneratedParentLinkCode code = parentLinkCodeService.generateUnique();
                StudentProfile profile = new StudentProfile();
                profile.setUser(user);
                profile.setSchoolClass(schoolClass);
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                profile.setBirthDate(input.birthDate());
                profile.setEnrollmentDate(LocalDate.now(clock));
                profile.setStatus(StudentStatus.STUDYING);
                profile.setParentLinkCodeHash(code.hash());
                profile.setParentLinkCodeCreatedAt(now);
                studentProfileRepository.saveAndFlush(profile);
                yield code.value();
            }
            case TEACHER -> {
                TeacherProfile profile = new TeacherProfile();
                profile.setUser(user);
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                profile.setBirthDate(input.birthDate());
                profile.setEmail(normalizeEmail(input.email()));
                profile.setPhone(normalizePhone(input.phone()));
                profile.getSubjects().add(subject);
                teacherProfileRepository.save(profile);
                yield null;
            }
            case PARENT -> {
                if (input.classId() != null || input.subjectId() != null) {
                    throw invalidRoleDetails();
                }
                ParentProfile profile = new ParentProfile();
                profile.setUser(user);
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                profile.setBirthDate(input.birthDate());
                profile.setEmail(normalizeEmail(input.email()));
                profile.setPhone(normalizePhone(input.phone()));
                profile = parentProfileRepository.saveAndFlush(profile);
                boolean primaryContact = !studentParentLinkRepository.existsByStudent_Id(requestedStudent.getId());
                studentParentLinkRepository.save(new StudentParentLink(
                    requestedStudent,
                    profile,
                    primaryContact
                ));
                yield null;
            }
            case ADMIN -> throw new IllegalStateException("Unsupported administrator creation flow");
        };
    }

    private void validateBirthDate(LocalDate birthDate, UserRole role) {
        int age = Period.between(birthDate, LocalDate.now(clock)).getYears();
        if (role == UserRole.STUDENT && (age < 5 || age > 20)) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_BIRTH_DATE",
                "Вік учня має бути від 5 до 20 років"
            );
        }
        if ((role == UserRole.TEACHER || role == UserRole.PARENT) && (age < 18 || age > 100)) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "ADULT_ROLE_REQUIRES_ADULT_AGE",
                "Для обраної ролі вік має бути від 18 до 100 років"
            );
        }
    }

    private ApplicationException invalidRoleDetails() {
        return new ApplicationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_ROLE_DETAILS",
            "Клас, предмет або код прив’язки не відповідає обраній ролі"
        );
    }

    private String normalizeLogin(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalName(String value) {
        return value == null || value.isBlank() ? null : normalizeName(value);
    }

    private String normalizePhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("380") && digits.length() == 12) {
            return "+" + digits;
        }
        if (digits.length() == 9) {
            return "+380" + digits;
        }
        throw new ApplicationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_PHONE",
            "Некоректний формат номера телефону"
        );
    }

    private AdminUserResponse toResponse(AppUser user) {
        return new AdminUserResponse(
            user.getId(),
            user.getLogin(),
            user.getEmail(),
            user.getRole(),
            user.getStatus(),
            user.isMustChangePassword(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
