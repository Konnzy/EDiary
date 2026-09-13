package com.ediary.backend.registration.service;

import com.ediary.backend.common.dto.PageResponse;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.registration.dto.request.CreateRegistrationRequest;
import com.ediary.backend.registration.dto.response.RegistrationDecisionResponse;
import com.ediary.backend.registration.dto.response.RegistrationOptionResponse;
import com.ediary.backend.registration.dto.response.RegistrationOptionsResponse;
import com.ediary.backend.registration.dto.response.RegistrationRequestResponse;
import com.ediary.backend.registration.dto.response.RegistrationSubmissionResponse;
import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.entity.RegistrationStatus;
import com.ediary.backend.registration.event.RegistrationApprovedEvent;
import com.ediary.backend.registration.event.RegistrationRejectedEvent;
import com.ediary.backend.registration.mapper.RegistrationRequestMapper;
import com.ediary.backend.registration.repository.RegistrationRequestRepository;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.entity.Subject;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.school.repository.SubjectRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.service.ParentLinkCodeService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationRequestService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RegistrationRequestRepository registrationRequestRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final ParentLinkCodeService parentLinkCodeService;
    private final AccountCreationService accountCreationService;
    private final RegistrationRequestMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public RegistrationSubmissionResponse submit(CreateRegistrationRequest input) {
        UserRole role = input.role();
        if (role == UserRole.ADMIN) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_SELF_REGISTRATION_FORBIDDEN",
                "Самостійна реєстрація адміністратора заборонена"
            );
        }

        String email = normalizeEmail(input.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                "Для цієї електронної пошти вже існує обліковий запис"
            );
        }
        if (registrationRequestRepository.existsByEmailIgnoreCaseAndStatus(email, RegistrationStatus.PENDING)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "REGISTRATION_ALREADY_PENDING",
                "Заявка з цією електронною поштою вже перебуває на розгляді"
            );
        }

        validateBirthDate(input.birthDate(), role);
        SchoolClass requestedClass = resolveClass(role, input.requestedClassId(), input.requestedSubjectId());
        Subject requestedSubject = resolveSubject(role, input.requestedClassId(), input.requestedSubjectId());
        StudentProfile requestedStudent = resolveStudent(role, input.parentLinkCode());
        LocalDateTime now = LocalDateTime.now(clock);
        RegistrationRequest request = new RegistrationRequest(
            normalizeName(input.firstName()),
            normalizeName(input.lastName()),
            normalizeOptionalName(input.patronymic()),
            email,
            normalizePhone(input.phone()),
            input.birthDate(),
            role,
            requestedClass,
            requestedSubject,
            requestedStudent,
            now
        );
        request = registrationRequestRepository.saveAndFlush(request);

        return new RegistrationSubmissionResponse(
            request.getId(),
            request.getStatus(),
            "Заявку прийнято. Результат розгляду буде надіслано електронною поштою.",
            request.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public RegistrationOptionsResponse getOptions() {
        List<RegistrationOptionResponse> classes = schoolClassRepository
            .findAllByAcademicYearValueOrderByNumberAscLetterAsc(currentAcademicYear())
            .stream()
            .map(item -> new RegistrationOptionResponse(item.getId(), item.getDisplayName()))
            .toList();
        List<RegistrationOptionResponse> subjects = subjectRepository
            .findAllByOrderByTitleAsc()
            .stream()
            .map(item -> new RegistrationOptionResponse(item.getId(), item.getTitle()))
            .toList();
        return new RegistrationOptionsResponse(classes, subjects);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationRequestResponse> findAll(
        RegistrationStatus status,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<RegistrationRequest> requests = status == null
            ? registrationRequestRepository.findAll(pageRequest)
            : registrationRequestRepository.findAllByStatus(status, pageRequest);
        return PageResponse.from(requests.map(mapper::toResponse));
    }

    @Transactional
    public RegistrationDecisionResponse approve(Long requestId, String reviewerLogin, String requestedLogin) {
        RegistrationRequest request = findPendingForUpdate(requestId);
        AppUser reviewer = requireActiveAdministrator(reviewerLogin);
        if (request.getRequestedRole() == UserRole.STUDENT) {
            ensureCurrentClass(request.getRequestedClass());
        }
        String login = normalizeLogin(requestedLogin);
        AccountProvisioningResult account = accountCreationService.createFrom(request, login);
        LocalDateTime now = LocalDateTime.now(clock);

        request.approve(reviewer, account.user(), now);
        registrationRequestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(new RegistrationApprovedEvent(
            request.getEmail(),
            request.getName(),
            account.user().getLogin(),
            account.temporaryPassword(),
            account.parentLinkCode()
        ));

        return new RegistrationDecisionResponse(
            request.getId(),
            request.getStatus(),
            account.user().getLogin(),
            now,
            "Заявку схвалено, обліковий запис створено"
        );
    }

    @Transactional
    public RegistrationDecisionResponse reject(Long requestId, String reviewerLogin, String reason) {
        RegistrationRequest request = findPendingForUpdate(requestId);
        AppUser reviewer = requireActiveAdministrator(reviewerLogin);
        String normalizedReason = normalizeRequiredText(reason);
        LocalDateTime now = LocalDateTime.now(clock);

        request.reject(reviewer, normalizedReason, now);
        registrationRequestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(new RegistrationRejectedEvent(
            request.getEmail(),
            request.getName(),
            normalizedReason
        ));

        return new RegistrationDecisionResponse(
            request.getId(),
            request.getStatus(),
            null,
            now,
            "Заявку відхилено"
        );
    }

    private RegistrationRequest findPendingForUpdate(Long requestId) {
        RegistrationRequest request = registrationRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "REGISTRATION_NOT_FOUND",
                "Заявку не знайдено"
            ));
        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "REGISTRATION_ALREADY_REVIEWED",
                "Цю заявку вже розглянуто"
            );
        }
        return request;
    }

    private AppUser requireActiveAdministrator(String login) {
        AppUser reviewer = userRepository.findByLoginIgnoreCase(login)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.FORBIDDEN,
                "ADMIN_ACCESS_REQUIRED",
                "Недостатньо прав для виконання операції"
            ));
        if (reviewer.getRole() != UserRole.ADMIN || reviewer.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(
                HttpStatus.FORBIDDEN,
                "ADMIN_ACCESS_REQUIRED",
                "Недостатньо прав для виконання операції"
            );
        }
        return reviewer;
    }

    private String normalizeLogin(String login) {
        return login.strip().toLowerCase(Locale.ROOT);
    }

    private SchoolClass resolveClass(UserRole role, Long classId, Long subjectId) {
        if (role != UserRole.STUDENT) {
            if (classId != null) throw invalidRoleDetails();
            return null;
        }
        if (classId == null || subjectId != null) {
            throw invalidRoleDetails();
        }
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "CLASS_NOT_FOUND",
                "Обраний клас не знайдено"
            ));
        ensureCurrentClass(schoolClass);
        return schoolClass;
    }

    private void ensureCurrentClass(SchoolClass schoolClass) {
        if (schoolClass == null || !currentAcademicYear().equals(schoolClass.getAcademicYear().getValue())) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CLASS_ACADEMIC_YEAR_MISMATCH",
                "Обраний клас не належить до поточного навчального року"
            );
        }
    }

    private String currentAcademicYear() {
        LocalDate today = LocalDate.now(clock);
        int startYear = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    private Subject resolveSubject(UserRole role, Long classId, Long subjectId) {
        if (role != UserRole.TEACHER) {
            if (subjectId != null) throw invalidRoleDetails();
            return null;
        }
        if (subjectId == null || classId != null) {
            throw invalidRoleDetails();
        }
        return subjectRepository.findById(subjectId)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "SUBJECT_NOT_FOUND",
                "Обраний предмет не знайдено"
            ));
    }

    private StudentProfile resolveStudent(UserRole role, String parentLinkCode) {
        if (role == UserRole.PARENT) {
            return parentLinkCodeService.requireActiveStudent(parentLinkCode);
        }
        if (parentLinkCode != null && !parentLinkCode.isBlank()) {
            throw invalidRoleDetails();
        }
        return null;
    }

    private ApplicationException invalidRoleDetails() {
        return new ApplicationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_ROLE_DETAILS",
            "Клас, предмет або код прив’язки не відповідає обраній ролі"
        );
    }

    private void validateBirthDate(LocalDate birthDate, UserRole role) {
        LocalDate today = LocalDate.now(clock);
        int age = Period.between(birthDate, today).getYears();

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

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    private String normalizeRequiredText(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "REJECTION_REASON_REQUIRED",
                "Вкажіть причину відхилення"
            );
        }
        return normalized;
    }
}
