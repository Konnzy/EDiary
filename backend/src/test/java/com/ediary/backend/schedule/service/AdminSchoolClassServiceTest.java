package com.ediary.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.dto.request.SaveSchoolClassRequest;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.entity.StudentClassMembership;
import com.ediary.backend.schedule.repository.AcademicYearRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.StudentClassMembershipRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.entity.TeacherProfile;
import com.ediary.backend.user.repository.StudentProfileRepository;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import java.util.Optional;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    @Mock private StudentClassMembershipRepository membershipRepository;
    @Mock private AcademicYearRepository academicYearRepository;

    private AdminSchoolClassService service;

    @BeforeEach
    void setUp() {
        service = new AdminSchoolClassService(
            schoolClassRepository,
            teacherProfileRepository,
            studentProfileRepository,
            membershipRepository,
            academicYearRepository,
            Clock.fixed(Instant.parse("2026-09-12T09:00:00Z"), ZoneOffset.UTC)
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

        AcademicYear year = academicYear("2026/2027");
        when(academicYearRepository.findById("2026/2027")).thenReturn(Optional.of(year));

        var response = service.create(new SaveSchoolClassRequest(7, "б", null, "2026/2027"));

        assertThat(response.number()).isEqualTo(7);
        assertThat(response.letter()).isEqualTo("Б");
        assertThat(response.limit()).isEqualTo(AdminSchoolClassService.CLASS_CAPACITY);
        verify(schoolClassRepository).saveAndFlush(any(SchoolClass.class));
    }

    @Test
    void rejectsDuplicateClass() {
        when(academicYearRepository.findById("2026/2027"))
            .thenReturn(Optional.of(academicYear("2026/2027")));
        when(schoolClassRepository.existsByAcademicYearValueAndNumberAndLetterIgnoreCase(
            "2026/2027", 7, "Б"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SaveSchoolClassRequest(7, "Б", null, "2026/2027")))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("SCHOOL_CLASS_ALREADY_EXISTS"));
    }

    @Test
    void listsOnlyClassesAndMembershipCountsForSelectedYear() {
        SchoolClass archived = schoolClass(8L);
        archived.setAcademicYear(academicYear("2025/2026"));
        when(schoolClassRepository.findAllByAcademicYearValueOrderByNumberAscLetterAsc("2025/2026"))
            .thenReturn(List.of(archived));
        when(membershipRepository.countBySchoolClassIdAndActiveTrue(8L)).thenReturn(5L);

        var classes = service.findAll(null, null, "2025/2026");

        assertThat(classes).hasSize(1);
        assertThat(classes.getFirst().academicYear()).isEqualTo("2025/2026");
        assertThat(classes.getFirst().studentCount()).isEqualTo(5);
        assertThat(classes.getFirst().editable()).isFalse();
        verify(schoolClassRepository).findAllByAcademicYearValueOrderByNumberAscLetterAsc("2025/2026");
    }

    @Test
    void doesNotCreateClassInPastAcademicYear() {
        assertThatThrownBy(() -> service.create(new SaveSchoolClassRequest(9, "А", null, "2025/2026")))
            .isInstanceOf(ApplicationException.class)
            .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                .isEqualTo("HISTORICAL_CLASS_READ_ONLY"));
        verify(schoolClassRepository, never()).saveAndFlush(any(SchoolClass.class));
    }

    @Test
    void rejectsStudentWhenClassHasReachedFixedCapacity() {
        SchoolClass schoolClass = schoolClass(9L);
        when(schoolClassRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schoolClass));
        when(membershipRepository.countBySchoolClassIdAndActiveTrue(9L))
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
        StudentClassMembership membership = new StudentClassMembership();
        membership.setStudent(student);
        membership.setSchoolClass(schoolClass);
        when(schoolClassRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(schoolClass));
        when(membershipRepository.findByStudentIdAndSchoolClassIdAndActiveTrue(14L, 9L))
            .thenReturn(Optional.of(membership));
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
        when(membershipRepository.countBySchoolClassId(9L)).thenReturn(1L);
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
        schoolClass.setAcademicYear(academicYear("2026/2027"));
        return schoolClass;
    }

    private AcademicYear academicYear(String value) {
        AcademicYear academicYear = new AcademicYear();
        academicYear.setValue(value);
        return academicYear;
    }
}
