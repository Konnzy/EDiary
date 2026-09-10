package com.ediary.backend.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.school.dto.request.SaveSchoolClassRequest;
import com.ediary.backend.school.entity.SchoolClass;
import com.ediary.backend.school.repository.SchoolClassRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSchoolClassServiceTest {

    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private StudentProfileRepository studentProfileRepository;

    private AdminSchoolClassService service;

    @BeforeEach
    void setUp() {
        service = new AdminSchoolClassService(
            schoolClassRepository,
            teacherProfileRepository,
            studentProfileRepository
        );
    }

    @Test
    void createsClassAndNormalizesLetter() {
        when(schoolClassRepository.saveAndFlush(any(SchoolClass.class)))
            .thenAnswer(invocation -> {
                SchoolClass value = invocation.getArgument(0);
                value.setId(5L);
                return value;
            });

        var response = service.create(new SaveSchoolClassRequest(7, "б", null));

        assertThat(response.number()).isEqualTo(7);
        assertThat(response.letter()).isEqualTo("Б");
        assertThat(response.limit()).isEqualTo(AdminSchoolClassService.CLASS_CAPACITY);
        verify(schoolClassRepository).saveAndFlush(any(SchoolClass.class));
    }

    @Test
    void rejectsDuplicateClass() {
        when(schoolClassRepository.existsByNumberAndLetterIgnoreCase(7, "Б")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SaveSchoolClassRequest(7, "Б", null)))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SCHOOL_CLASS_ALREADY_EXISTS"));
    }

    @Test
    void rejectsStudentWhenClassHasReachedFixedCapacity() {
        SchoolClass schoolClass = schoolClass(9L);
        when(schoolClassRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schoolClass));
        when(studentProfileRepository.countBySchoolClassId(9L))
            .thenReturn((long) AdminSchoolClassService.CLASS_CAPACITY);

        assertThatThrownBy(() -> service.addStudent(9L, 14L))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SCHOOL_CLASS_IS_FULL"));
    }

    @Test
    void removesOnlyStudentClassAssignment() {
        SchoolClass schoolClass = schoolClass(9L);
        StudentProfile student = new StudentProfile();
        student.setId(14L);
        student.setUser(new AppUser());
        student.setSchoolClass(schoolClass);
        when(studentProfileRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(student));
        when(studentProfileRepository.saveAndFlush(student)).thenReturn(student);

        service.removeStudent(9L, 14L);

        assertThat(student.getSchoolClass()).isNull();
        verify(studentProfileRepository).saveAndFlush(student);
    }

    @Test
    void deletesNewClassWithoutRelations() {
        SchoolClass schoolClass = schoolClass(9L);
        when(schoolClassRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schoolClass));

        service.delete(9L);

        verify(schoolClassRepository).delete(schoolClass);
        verify(schoolClassRepository).flush();
    }

    @Test
    void rejectsClassDeletionWhenAnyRelationsExist() {
        SchoolClass schoolClass = schoolClass(9L);
        schoolClass.setClassTeacher(new TeacherProfile());
        when(schoolClassRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schoolClass));
        when(studentProfileRepository.countBySchoolClassId(9L)).thenReturn(1L);
        when(schoolClassRepository.hasSchedule(9L)).thenReturn(true);
        when(schoolClassRepository.hasRegistrationRequests(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(9L))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> {
                ApplicationException applicationError = (ApplicationException) error;
                assertThat(applicationError.getCode()).isEqualTo("SCHOOL_CLASS_HAS_RELATIONS");
                assertThat(applicationError.getMessage())
                    .contains("класного керівника", "учнів", "розклад", "реєстраційних заявках");
            });
        verify(schoolClassRepository, never()).delete(any(SchoolClass.class));
    }

    private SchoolClass schoolClass(Long id) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setNumber(9);
        schoolClass.setLetter("А");
        schoolClass.setLimit(AdminSchoolClassService.CLASS_CAPACITY);
        return schoolClass;
    }
}
