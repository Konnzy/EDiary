package com.ediary.backend.user.parent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentParentId;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.repository.ParentProfileRepository;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentAccessServiceTest {

    @Mock
    private StudentParentLinkRepository studentParentLinkRepository;

    @Mock
    private ParentProfileRepository parentProfileRepository;

    @Mock
    private ParentLinkCodeService parentLinkCodeService;

    private ParentAccessService service;

    @BeforeEach
    void setUp() {
        service = new ParentAccessService(
            studentParentLinkRepository,
            parentProfileRepository,
            parentLinkCodeService
        );
    }

    @Test
    void returnsOnlyLinksSelectedByAuthenticatedParentLogin() {
        StudentParentLink link = linkedStudent();
        when(studentParentLinkRepository.findAllByParentLogin("parent.one")).thenReturn(List.of(link));

        var children = service.findChildren("parent.one");

        assertThat(children).hasSize(1);
        assertThat(children.getFirst().studentId()).isEqualTo(7L);
        assertThat(children.getFirst().className()).isEqualTo("9-А");
    }

    @Test
    void hidesWhetherForeignStudentExists() {
        when(studentParentLinkRepository.findByParentLoginAndStudentId("parent.one", 99L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findChild("parent.one", 99L))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("PARENT_CHILD_NOT_FOUND"));
    }

    @Test
    void linksStudentFromCodeToAuthenticatedParent() {
        StudentParentLink existingView = linkedStudent();
        StudentProfile student = existingView.getStudent();
        ParentProfile parent = new ParentProfile();
        parent.setId(12L);
        StudentParentId linkId = new StudentParentId(student.getId(), parent.getId());

        when(parentProfileRepository.findByUser_LoginIgnoreCase("parent.one"))
            .thenReturn(Optional.of(parent));
        when(parentLinkCodeService.requireActiveStudent("ABCD-1234")).thenReturn(student);
        when(studentParentLinkRepository.existsById(linkId)).thenReturn(false);
        when(studentParentLinkRepository.existsByStudent_Id(student.getId())).thenReturn(false);
        when(studentParentLinkRepository.save(org.mockito.ArgumentMatchers.any(StudentParentLink.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var linkedChild = service.linkChild("parent.one", "ABCD-1234");

        assertThat(linkedChild.studentId()).isEqualTo(student.getId());
        assertThat(linkedChild.primaryContact()).isTrue();
        verify(parentLinkCodeService).requireActiveStudent("ABCD-1234");
        verify(studentParentLinkRepository).save(org.mockito.ArgumentMatchers.any(StudentParentLink.class));
    }

    @Test
    void rejectsChildAlreadyLinkedToAuthenticatedParent() {
        StudentProfile student = linkedStudent().getStudent();
        ParentProfile parent = new ParentProfile();
        parent.setId(12L);
        StudentParentId linkId = new StudentParentId(student.getId(), parent.getId());

        when(parentProfileRepository.findByUser_LoginIgnoreCase("parent.one"))
            .thenReturn(Optional.of(parent));
        when(parentLinkCodeService.requireActiveStudent("ABCD-1234")).thenReturn(student);
        when(studentParentLinkRepository.existsById(linkId)).thenReturn(true);

        assertThatThrownBy(() -> service.linkChild("parent.one", "ABCD-1234"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("CHILD_ALREADY_LINKED"));
    }

    private StudentParentLink linkedStudent() {
        AppUser studentUser = new AppUser();
        studentUser.setLogin("student.one");
        studentUser.setEmail("student@example.com");
        StudentProfile student = new StudentProfile();
        student.setId(7L);
        student.setUser(studentUser);
        student.setName("Іван");
        student.setSurname("Петренко");
        student.setStatus(StudentStatus.STUDYING);
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setNumber(9);
        schoolClass.setLetter("А");
        student.setSchoolClass(schoolClass);
        ParentProfile parent = new ParentProfile();
        parent.setId(8L);
        return new StudentParentLink(student, parent, true);
    }
}
