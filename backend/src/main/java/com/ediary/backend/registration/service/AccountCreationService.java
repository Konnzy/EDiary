package com.ediary.backend.registration.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import com.ediary.backend.user.repository.UserRepository;
import com.ediary.backend.user.service.ParentLinkCodeService;
import com.ediary.backend.user.service.TemporaryPasswordGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountCreationService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final StudentParentLinkRepository studentParentLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final ParentLinkCodeService parentLinkCodeService;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountProvisioningResult createFrom(RegistrationRequest request, String login) {
        if (userRepository.existsByLoginIgnoreCase(login)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "LOGIN_ALREADY_EXISTS",
                "Цей логін уже використовується. Введіть інший логін"
            );
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                "Для цієї електронної пошти вже існує обліковий запис"
            );
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        LocalDateTime now = LocalDateTime.now(clock);

        AppUser user = new AppUser();
        user.setLogin(login);
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setRole(request.getRequestedRole());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepository.saveAndFlush(user);

        String parentLinkCode = createRoleProfile(request, user, now);
        return new AccountProvisioningResult(user, temporaryPassword, parentLinkCode);
    }

    private String createRoleProfile(RegistrationRequest request, AppUser user, LocalDateTime now) {
        return switch (request.getRequestedRole()) {
            case STUDENT -> createStudentProfile(request, user, now);
            case TEACHER -> {
                createTeacherProfile(request, user);
                yield null;
            }
            case PARENT -> {
                createParentProfile(request, user);
                yield null;
            }
            case ADMIN -> throw new ApplicationException(
                HttpStatus.BAD_REQUEST,
                "ADMIN_SELF_REGISTRATION_FORBIDDEN",
                "Самостійна реєстрація адміністратора заборонена"
            );
        };
    }

    private String createStudentProfile(RegistrationRequest request, AppUser user, LocalDateTime now) {
        ParentLinkCodeService.GeneratedParentLinkCode code = parentLinkCodeService.generateUnique();
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setSchoolClass(request.getRequestedClass());
        profile.setName(request.getName());
        profile.setSurname(request.getSurname());
        profile.setPatronymic(request.getPatronymic());
        profile.setBirthDate(request.getBirthDate());
        profile.setEnrollmentDate(LocalDate.now(clock));
        profile.setStatus(StudentStatus.STUDYING);
        profile.setParentLinkCodeHash(code.hash());
        profile.setParentLinkCodeCreatedAt(now);
        studentProfileRepository.saveAndFlush(profile);
        return code.value();
    }

    private void createTeacherProfile(RegistrationRequest request, AppUser user) {
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setName(request.getName());
        profile.setSurname(request.getSurname());
        profile.setPatronymic(request.getPatronymic());
        profile.setBirthDate(request.getBirthDate());
        profile.setEmail(request.getEmail());
        profile.setPhone(request.getPhone());
        profile.getSubjects().add(request.getRequestedSubject());
        teacherProfileRepository.save(profile);
    }

    private void createParentProfile(RegistrationRequest request, AppUser user) {
        if (request.getRequestedStudent() == null) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "PARENT_REQUEST_REQUIRES_STUDENT",
                "Заявку батьків створено за старими правилами. Відхиліть її та попросіть подати нову з кодом дитини"
            );
        }
        ParentProfile profile = new ParentProfile();
        profile.setUser(user);
        profile.setName(request.getName());
        profile.setSurname(request.getSurname());
        profile.setPatronymic(request.getPatronymic());
        profile.setBirthDate(request.getBirthDate());
        profile.setEmail(request.getEmail());
        profile.setPhone(request.getPhone());
        profile = parentProfileRepository.saveAndFlush(profile);
        boolean primaryContact = !studentParentLinkRepository.existsByStudent_Id(
            request.getRequestedStudent().getId()
        );
        studentParentLinkRepository.save(new StudentParentLink(
            request.getRequestedStudent(),
            profile,
            primaryContact
        ));
    }

}
