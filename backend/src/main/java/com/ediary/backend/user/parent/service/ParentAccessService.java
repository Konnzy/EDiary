package com.ediary.backend.user.parent.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.parent.dto.ParentChildResponse;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentParentId;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParentAccessService {

    private final StudentParentLinkRepository studentParentLinkRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentLinkCodeService parentLinkCodeService;

    @Transactional(readOnly = true)
    public List<ParentChildResponse> findChildren(String parentLogin) {
        return studentParentLinkRepository.findAllByParentLogin(parentLogin)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ParentChildResponse findChild(String parentLogin, Long studentId) {
        StudentParentLink link = studentParentLinkRepository
            .findByParentLoginAndStudentId(parentLogin, studentId)
            .orElseThrow(this::childNotFound);
        return toResponse(link);
    }

    @Transactional
    public ParentChildResponse linkChild(String parentLogin, String code) {
        ParentProfile parent = parentProfileRepository.findByUser_LoginIgnoreCase(parentLogin)
            .orElseThrow(() -> new ApplicationException(
                HttpStatus.NOT_FOUND,
                "PARENT_PROFILE_NOT_FOUND",
                "Профіль опікуна не знайдено"
            ));
        StudentProfile student = parentLinkCodeService.requireActiveStudent(code);
        StudentParentId linkId = new StudentParentId(student.getId(), parent.getId());

        if (studentParentLinkRepository.existsById(linkId)) {
            throw new ApplicationException(
                HttpStatus.CONFLICT,
                "CHILD_ALREADY_LINKED",
                "Ця дитина вже додана до вашого профілю"
            );
        }

        boolean primaryContact = !studentParentLinkRepository.existsByStudent_Id(student.getId());
        StudentParentLink link = studentParentLinkRepository.save(
            new StudentParentLink(student, parent, primaryContact)
        );
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public StudentProfile requireAccessibleStudent(String parentLogin, Long studentId) {
        return studentParentLinkRepository.findByParentLoginAndStudentId(parentLogin, studentId)
            .map(StudentParentLink::getStudent)
            .orElseThrow(this::childNotFound);
    }

    private ParentChildResponse toResponse(StudentParentLink link) {
        StudentProfile student = link.getStudent();
        return new ParentChildResponse(
            student.getId(),
            student.getName(),
            student.getSurname(),
            student.getUser().getLogin(),
            student.getUser().getEmail(),
            student.getSchoolClass() == null ? null : student.getSchoolClass().getDisplayName(),
            student.getStatus(),
            link.isPrimaryContact()
        );
    }

    private ApplicationException childNotFound() {
        return new ApplicationException(
            HttpStatus.NOT_FOUND,
            "PARENT_CHILD_NOT_FOUND",
            "Учня не знайдено"
        );
    }
}
