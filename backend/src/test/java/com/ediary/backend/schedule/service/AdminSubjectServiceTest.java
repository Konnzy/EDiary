package com.ediary.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.dto.request.SaveSubjectRequest;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSubjectServiceTest {

    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;

    private AdminSubjectService service;

    @BeforeEach
    void setUp() {
        service = new AdminSubjectService(subjectRepository, teacherProfileRepository);
    }

    @Test
    void createsSubjectAndAssignsSeveralTeachers() {
        TeacherProfile firstTeacher = activeTeacher(1L, "Іваненко", "Іван");
        TeacherProfile secondTeacher = activeTeacher(2L, "Петренко", "Олена");
        when(subjectRepository.saveAndFlush(any(Subject.class))).thenAnswer(invocation -> {
            Subject subject = invocation.getArgument(0);
            subject.setId(7L);
            return subject;
        });
        when(teacherProfileRepository.findAllAssignedToSubject(7L)).thenReturn(List.of());
        when(teacherProfileRepository.findAllById(Set.of(1L, 2L)))
            .thenReturn(List.of(firstTeacher, secondTeacher));

        var response = service.create(new SaveSubjectRequest("  Математика  ", Set.of(1L, 2L)));

        assertThat(response.title()).isEqualTo("Математика");
        assertThat(response.teachers()).hasSize(2);
        assertThat(firstTeacher.getSubjects()).extracting(Subject::getTitle).containsExactly("Математика");
        assertThat(secondTeacher.getSubjects()).extracting(Subject::getTitle).containsExactly("Математика");
        verify(teacherProfileRepository).saveAll(any());
    }

    @Test
    void rejectsDuplicateSubjectTitleIgnoringCase() {
        when(subjectRepository.existsByTitleIgnoreCase("Математика")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SaveSubjectRequest("Математика", Set.of())))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SUBJECT_ALREADY_EXISTS"));
    }

    @Test
    void rejectsRemovingTeacherAssignmentUsedBySchedule() {
        Subject subject = subject(7L, "Математика");
        TeacherProfile teacher = activeTeacher(1L, "Іваненко", "Іван");
        teacher.getSubjects().add(subject);
        when(subjectRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(subject));
        when(teacherProfileRepository.findAllAssignedToSubject(7L)).thenReturn(List.of(teacher));
        when(subjectRepository.isTeacherAssignmentUsedInSchedule(7L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, new SaveSubjectRequest("Математика", Set.of())))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("TEACHER_SUBJECT_USED_IN_SCHEDULE"));
    }

    @Test
    void deletesSubjectWithoutRelations() {
        Subject subject = subject(7L, "Математика");
        when(subjectRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(subject));

        service.delete(7L);

        verify(subjectRepository).delete(subject);
        verify(subjectRepository).flush();
    }

    @Test
    void rejectsDeletingSubjectAssignedToTeacher() {
        Subject subject = subject(7L, "Математика");
        when(subjectRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(subject));
        when(subjectRepository.hasTeachers(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SUBJECT_HAS_RELATIONS"));
        verify(subjectRepository, never()).delete(any(Subject.class));
    }

    private Subject subject(Long id, String title) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setTitle(title);
        return subject;
    }

    private TeacherProfile activeTeacher(Long id, String surname, String name) {
        AppUser user = new AppUser();
        user.setStatus(UserStatus.ACTIVE);
        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(id);
        teacher.setSurname(surname);
        teacher.setName(name);
        teacher.setUser(user);
        return teacher;
    }
}
