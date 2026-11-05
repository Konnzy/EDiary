package com.ediary.backend.user.admin.service;
import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.repository.AttendanceRepository;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.journal.repository.StudentHomeworkRepository;
import com.ediary.backend.notification.repository.MessageRepository;
import com.ediary.backend.notification.repository.UserNotificationSettingsRepository;
import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.user.admin.dto.CreateAdminUserRequest;
import com.ediary.backend.user.admin.dto.AdminUserCreationResponse;
import com.ediary.backend.user.admin.dto.AdminUserResponse;
import com.ediary.backend.user.admin.dto.UserDeletionEligibilityResponse;
import com.ediary.backend.user.admin.dto.UpdateAdminUserRequest;
import com.ediary.backend.user.admin.dto.AdminUserActionResponse;
import com.ediary.backend.user.admin.event.AdminUserCreatedEvent;
import com.ediary.backend.user.admin.event.AdminUserPasswordResetEvent;
import com.ediary.backend.user.admin.event.AdminUserRoleChangedEvent;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.AdministratorProfile;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.parent.service.ParentLinkCodeService;
import com.ediary.backend.user.password.repository.PasswordResetTokenRepository;
import com.ediary.backend.user.password.service.TemporaryPasswordGenerator;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.AdministratorProfileRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.session.repository.UserSessionRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AdministratorProfileRepository administratorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final StudentParentLinkRepository studentParentLinkRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ScheduleRepository scheduleRepository;
    private final StudentClassMembershipRepository studentClassMembershipRepository;
    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final MessageRepository messageRepository;
    private final UserNotificationSettingsRepository notificationSettingsRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final ParentLinkCodeService parentLinkCodeService;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;
    private final AuditService audit;

    @Transactional
    public AdminUserCreationResponse create(CreateAdminUserRequest input) {
        CreationTargets targets = validateAndResolveCreation(input);
        String login = normalizeLogin(input.login());
        String email = normalizeEmail(input.email());
        SchoolClass schoolClass = targets.schoolClass();
        Subject subject = targets.subject();
        StudentProfile requestedStudent = targets.student();
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
            email, normalizeName(input.firstName()), login, temporaryPassword, parentLinkCode
        ));
        audit.record("USER_CREATED", "USER", user.getId(), of(), of("role", user.getRole(), "status", user.getStatus()));
        return new AdminUserCreationResponse(toResponse(user),
            "Користувача створено. Лист із даними для входу передано на надсилання.");
    }

    // Called within the import transaction; validation does not create accounts or publish mail.
    public void validateCreation(CreateAdminUserRequest input) {
        validateAndResolveCreation(input);
    }

    private CreationTargets validateAndResolveCreation(CreateAdminUserRequest input) {
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
        normalizePhone(input.phone());

        SchoolClass schoolClass = resolveClass(input);
        Subject subject = resolveSubject(input);
        StudentProfile requestedStudent = resolveStudent(input);
        return new CreationTargets(schoolClass, subject, requestedStudent);
    }

    private record CreationTargets(SchoolClass schoolClass, Subject subject, StudentProfile student) {}

    @Transactional(readOnly = true)
    public AdminUserResponse findCurrent(String login) {
        AppUser user = userRepository.findByLoginIgnoreCase(login)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Користувача не знайдено"
            ));
        return toResponse(user);
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
            String[] nameParts = normalizedQuery.toLowerCase(Locale.ROOT).split("\\s+");
            specification = specification.and((root, criteriaQuery, builder) -> builder.or(
                profileNameMatches(criteriaQuery, builder, root, StudentProfile.class, nameParts),
                profileNameMatches(criteriaQuery, builder, root, ParentProfile.class, nameParts),
                profileNameMatches(criteriaQuery, builder, root, TeacherProfile.class, nameParts),
                profileNameMatches(criteriaQuery, builder, root, AdministratorProfile.class, nameParts)
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

        UserStatus previousStatus = user.getStatus();
        if (user.getStatus() != nextStatus) {
            user.setStatus(nextStatus);
            user.setUpdatedAt(LocalDateTime.now(clock));
            userRepository.saveAndFlush(user);
        }
        if (nextStatus == UserStatus.BLOCKED) {
            expireSessions(user.getLogin());
        }
        audit.record("USER_STATUS_CHANGED", "USER", userId, of("status", previousStatus), of("status", user.getStatus()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse update(Long userId, UpdateAdminUserRequest input) {
        AppUser user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Користувача не знайдено"
            ));
        String login = normalizeLogin(input.login());
        String email = normalizeEmail(input.email());
        String firstName = normalizeName(input.firstName());
        String lastName = normalizeName(input.lastName());
        String patronymic = normalizeOptionalName(input.patronymic());
        ensureUniqueCredentials(userId, login, email);

        UserRole previousRole = user.getRole();
        if ((previousRole == UserRole.ADMIN || input.role() == UserRole.ADMIN)
            && previousRole != input.role()) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "ADMIN_ROLE_IS_PROTECTED",
                "Роль адміністратора змінюється лише через окремий захищений процес"
            );
        }

        String previousLogin = user.getLogin();
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(email);
        String parentLinkCode = null;
        if (previousRole == input.role()) {
            updateRoleProfile(
                userId,
                previousRole,
                firstName,
                lastName,
                patronymic,
                email,
                input.phone()
            );
        } else {
            String blocker = findRoleChangeBlocker(user);
            if (blocker != null) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "USER_ROLE_HAS_RELATED_DATA",
                    blocker + ". Спочатку приберіть пов’язані дані або залиште поточну роль"
                );
            }
            ProfileIdentity previousIdentity = requireProfileIdentity(user);
            ProfileIdentity identity = new ProfileIdentity(
                firstName,
                lastName,
                patronymic,
                previousIdentity.birthDate(),
                previousIdentity.phone()
            );
            validateBirthDate(identity.birthDate(), input.role());
            parentLinkCode = replaceRoleProfile(
                userId,
                previousRole,
                input.role(),
                identity,
                email,
                input.phone()
            );
            user.setRole(input.role());
        }

        user.setLogin(login);
        user.setEmail(email);
        user.setUpdatedAt(LocalDateTime.now(clock));
        userRepository.saveAndFlush(user);

        boolean securityIdentityChanged = !previousLogin.equalsIgnoreCase(login)
            || previousRole != input.role();
        if (securityIdentityChanged) {
            revokeSessions(user, previousLogin, "ADMIN_UPDATED_ACCOUNT");
        }
        if (previousRole != input.role()) {
            eventPublisher.publishEvent(new AdminUserRoleChangedEvent(
                email,
                login,
                input.role(),
                parentLinkCode
            ));
        }
        audit.record("USER_UPDATED", "USER", userId, of("role", previousRole),
            of("role", user.getRole(), "loginChanged", !previousLogin.equalsIgnoreCase(login), "emailChanged", emailChanged, "profileChanged", true));
        return toResponse(user);
    }

    @Transactional
    public AdminUserActionResponse resetPassword(Long userId) {
        AppUser user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Користувача не знайдено"
            ));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CANNOT_RESET_DELETED_USER",
                "Неможливо скинути пароль видаленого користувача"
            );
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        LocalDateTime now = LocalDateTime.now(clock);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setUpdatedAt(now);
        userRepository.saveAndFlush(user);
        passwordResetTokenRepository.markAllUnusedAsUsed(userId);
        revokeSessions(user, user.getLogin(), "ADMIN_PASSWORD_RESET");
        eventPublisher.publishEvent(new AdminUserPasswordResetEvent(
            user.getEmail(),
            user.getLogin(),
            temporaryPassword
        ));

        audit.record("USER_PASSWORD_RESET", "USER", userId, of(), of("passwordChanged", true, "mustChangePassword", true));
        return new AdminUserActionResponse(
            "Новий тимчасовий пароль передано на надсилання користувачу"
        );
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
        parentProfileRepository.deleteByUser_Id(userId);
        teacherProfileRepository.deleteByUser_Id(userId);
        studentProfileRepository.deleteByUser_Id(userId);
        userRepository.delete(user);
        audit.record("USER_DELETED", "USER", userId, of("role", user.getRole(), "status", user.getStatus()), of("deleted", true));
    }

    private AppUser requireUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Користувача не знайдено"
            ));
    }

    private void updateRoleProfile(
        Long userId,
        UserRole role,
        String firstName,
        String lastName,
        String patronymic,
        String email,
        String phone
    ) {
        switch (role) {
            case STUDENT -> {
                StudentProfile profile = studentProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> missingRoleProfile(role));
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                studentProfileRepository.save(profile);
            }
            case TEACHER -> {
                TeacherProfile profile = teacherProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> missingRoleProfile(role));
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                profile.setEmail(email);
                profile.setPhone(normalizePhone(phone));
                teacherProfileRepository.save(profile);
            }
            case PARENT -> {
                ParentProfile profile = parentProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> missingRoleProfile(role));
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                profile.setEmail(email);
                profile.setPhone(normalizePhone(phone));
                parentProfileRepository.save(profile);
            }
            case ADMIN -> {
                AdministratorProfile profile = administratorProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> missingRoleProfile(role));
                profile.setName(firstName);
                profile.setSurname(lastName);
                profile.setPatronymic(patronymic);
                administratorProfileRepository.save(profile);
            }
        }
    }

    private ProfileIdentity requireProfileIdentity(AppUser user) {
        return findProfileIdentity(user)
            .orElseThrow(() -> missingRoleProfile(user.getRole()));
    }

    private Optional<ProfileIdentity> findProfileIdentity(AppUser user) {
        return switch (user.getRole()) {
            case STUDENT -> studentProfileRepository.findByUser_Id(user.getId())
                .map(profile -> new ProfileIdentity(
                    profile.getName(),
                    profile.getSurname(),
                    profile.getPatronymic(),
                    profile.getBirthDate(),
                    null
                ));
            case TEACHER -> teacherProfileRepository.findByUser_Id(user.getId())
                .map(profile -> new ProfileIdentity(
                    profile.getName(),
                    profile.getSurname(),
                    profile.getPatronymic(),
                    profile.getBirthDate(),
                    profile.getPhone()
                ));
            case PARENT -> parentProfileRepository.findByUser_Id(user.getId())
                .map(profile -> new ProfileIdentity(
                    profile.getName(),
                    profile.getSurname(),
                    profile.getPatronymic(),
                    profile.getBirthDate(),
                    profile.getPhone()
                ));
            case ADMIN -> administratorProfileRepository.findByUser_Id(user.getId())
                .map(profile -> new ProfileIdentity(
                    profile.getName(),
                    profile.getSurname(),
                    profile.getPatronymic(),
                    null,
                    null
                ));
        };
    }

    private String replaceRoleProfile(
        Long userId,
        UserRole previousRole,
        UserRole nextRole,
        ProfileIdentity identity,
        String email,
        String phone
    ) {
        switch (previousRole) {
            case STUDENT -> studentProfileRepository.deleteByUser_Id(userId);
            case TEACHER -> teacherProfileRepository.deleteByUser_Id(userId);
            case PARENT -> parentProfileRepository.deleteByUser_Id(userId);
            case ADMIN -> throw missingRoleProfile(previousRole);
        }

        AppUser userReference = userRepository.getReferenceById(userId);
        return switch (nextRole) {
            case STUDENT -> {
                ParentLinkCodeService.GeneratedParentLinkCode code = parentLinkCodeService.generateUnique();
                StudentProfile profile = new StudentProfile();
                profile.setUser(userReference);
                profile.setName(identity.name());
                profile.setSurname(identity.surname());
                profile.setPatronymic(identity.patronymic());
                profile.setBirthDate(identity.birthDate());
                profile.setEnrollmentDate(LocalDate.now(clock));
                profile.setStatus(StudentStatus.STUDYING);
                profile.setParentLinkCodeHash(code.hash());
                profile.setParentLinkCodeCreatedAt(LocalDateTime.now(clock));
                studentProfileRepository.saveAndFlush(profile);
                yield code.value();
            }
            case TEACHER -> {
                TeacherProfile profile = new TeacherProfile();
                profile.setUser(userReference);
                profile.setName(identity.name());
                profile.setSurname(identity.surname());
                profile.setPatronymic(identity.patronymic());
                profile.setBirthDate(identity.birthDate());
                profile.setEmail(email);
                profile.setPhone(normalizePhone(phone));
                teacherProfileRepository.saveAndFlush(profile);
                yield null;
            }
            case PARENT -> {
                ParentProfile profile = new ParentProfile();
                profile.setUser(userReference);
                profile.setName(identity.name());
                profile.setSurname(identity.surname());
                profile.setPatronymic(identity.patronymic());
                profile.setBirthDate(identity.birthDate());
                profile.setEmail(email);
                profile.setPhone(normalizePhone(phone));
                parentProfileRepository.saveAndFlush(profile);
                yield null;
            }
            case ADMIN -> throw new IllegalStateException("Administrator role is protected");
        };
    }

    private ApplicationException missingRoleProfile(UserRole role) {
        return new ApplicationException(
            HttpStatus.CONFLICT,
            "USER_PROFILE_NOT_FOUND",
            "Не знайдено профіль для ролі «" + role + "»"
        );
    }

    private String findRoleChangeBlocker(AppUser user) {
        return switch (user.getRole()) {
            case STUDENT -> findStudentDeletionBlocker(user.getId());
            case TEACHER -> findTeacherDeletionBlocker(user.getId());
            case PARENT -> findParentDeletionBlocker(user.getId());
            case ADMIN -> "Роль адміністратора захищена від зміни";
        };
    }

    private String findDeletionBlocker(AppUser user) {
        Long userId = user.getId();
        if (user.getRole() == UserRole.ADMIN) {
            return "Облікові записи адміністраторів не можна видаляти через розділ користувачів";
        }
        if (messageRepository.existsBySender_IdOrReceiver_Id(userId, userId)) {
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
        if (gradeRepository.existsByStudent_User_Id(userId)) {
            return "Учень має оцінки. Обліковий запис можна лише заблокувати";
        }
        if (attendanceRepository.existsByStudent_User_Id(userId)) {
            return "Учень має історію відвідування. Обліковий запис можна лише заблокувати";
        }
        if (studentHomeworkRepository.existsByStudent_User_Id(userId)) {
            return "Учень має історію домашніх завдань. Обліковий запис можна лише заблокувати";
        }
        if (studentParentLinkRepository.existsByStudent_User_Id(userId)) {
            return "Учень пов’язаний з обліковим записом батьків. Обліковий запис можна лише заблокувати";
        }
        if (studentClassMembershipRepository.existsByStudent_User_Id(userId)) {
            return "Учень має історію навчання в класі. Обліковий запис можна лише заблокувати";
        }
        if (studentProfileRepository.existsByUser_IdAndSchoolClassIsNotNull(userId)) {
            return "Учень належить до класу. Обліковий запис можна лише заблокувати";
        }
        return null;
    }

    private String findTeacherDeletionBlocker(Long userId) {
        if (scheduleRepository.existsByTeacher_User_Id(userId)) {
            return "Учитель має заняття в розкладі. Обліковий запис можна лише заблокувати";
        }
        if (schoolClassRepository.existsByClassTeacher_User_Id(userId)) {
            return "Учитель призначений класним керівником. Обліковий запис можна лише заблокувати";
        }
        if (teacherProfileRepository.hasAssignedSubjects(userId)) {
            return "Учитель призначений на предмет. Обліковий запис можна лише заблокувати";
        }
        return null;
    }

    private String findParentDeletionBlocker(Long userId) {
        if (studentParentLinkRepository.existsByParent_User_Id(userId)) {
            return "Обліковий запис батьків пов’язаний з учнем. Його можна лише заблокувати";
        }
        return null;
    }

    private void deleteRegistrationData(Long userId) {
        registrationRequestRepository.deleteByRequestedStudent_User_Id(userId);
        registrationRequestRepository.deleteByProcessedUser_Id(userId);
    }

    private void deleteTechnicalData(Long userId) {
        passwordResetTokenRepository.deleteAllByUser_Id(userId);
        userSessionRepository.deleteAllByUser_Id(userId);
        notificationSettingsRepository.deleteByUser_Id(userId);
    }

    private void expireSessions(String login) {
        sessionRegistry.getAllSessions(login, false).forEach(session -> session.expireNow());
    }

    private void revokeSessions(AppUser user, String sessionLogin, String reason) {
        userSessionRepository.revokeActiveSessions(user.getId(), LocalDateTime.now(clock), reason);
        expireSessions(sessionLogin);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.strip();
    }

    private <T> Predicate profileNameMatches(
        CriteriaQuery<?> criteriaQuery,
        CriteriaBuilder builder,
        Root<AppUser> user,
        Class<T> profileType,
        String[] nameParts
    ) {
        Subquery<Long> profileQuery = criteriaQuery.subquery(Long.class);
        Root<T> profile = profileQuery.from(profileType);
        var userId = profile.<AppUser>get("user").<Long>get("id");
        var patronymic = builder.coalesce(profile.<String>get("patronymic"), "");
        Predicate[] matchingParts = java.util.Arrays.stream(nameParts)
            .map(part -> {
                String pattern = "%" + part + "%";
                return builder.or(
                    builder.like(builder.lower(profile.<String>get("surname")), pattern),
                    builder.like(builder.lower(profile.<String>get("name")), pattern),
                    builder.like(builder.lower(patronymic), pattern)
                );
            })
            .toArray(Predicate[]::new);
        profileQuery.select(userId)
            .where(
                builder.equal(userId, user.<Long>get("id")),
                builder.and(matchingParts)
            );
        return builder.exists(profileQuery);
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

    private void ensureUniqueCredentials(Long userId, String login, String email) {
        if (userRepository.existsByLoginIgnoreCaseAndIdNot(login, userId)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "LOGIN_ALREADY_EXISTS",
                "Цей логін уже використовується. Введіть інший логін"
            );
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
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
            SchoolClass schoolClass = schoolClassRepository.findByIdForUpdate(input.classId())
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
            if (schoolClass.isDraft()) {
                throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CLASS_TEACHER_REQUIRED",
                    "До класу можна зараховувати учнів після призначення класного керівника"
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
        ProfileIdentity identity = findProfileIdentity(user)
            .orElse(new ProfileIdentity(null, null, null, null, null));
        return new AdminUserResponse(
            user.getId(),
            user.getLogin(),
            identity.name(),
            identity.surname(),
            identity.patronymic(),
            user.getEmail(),
            identity.phone(),
            user.getRole(),
            user.getStatus(),
            user.isMustChangePassword(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private record ProfileIdentity(
        String name,
        String surname,
        String patronymic,
        LocalDate birthDate,
        String phone
    ) {
    }
}
