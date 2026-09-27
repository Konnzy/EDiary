package com.ediary.backend.user.teacher.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.user.entity.AppUser;
import com.ediary.backend.user.entity.ParentProfile;
import com.ediary.backend.user.entity.StudentParentLink;
import com.ediary.backend.user.entity.StudentProfile;
import com.ediary.backend.user.repository.StudentParentLinkRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherStudentsServiceTest {
    @Mock private ScheduleRepository schedules;
    @Mock private StudentProfileRepository students;
    @Mock private StudentParentLinkRepository parents;

    private TeacherStudentsService service() {
        return new TeacherStudentsService(schedules, students, parents,
            Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void onlyLoadsAssignedClassesAndDeduplicatesSubjects() {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        schoolClass.setNumber(9);
        schoolClass.setLetter("А");
        var math = assignment(schoolClass, 10L, "Математика", true);
        var inactive = assignment(schoolClass, 11L, "Фізика", false);
        when(schedules.findJournalAssignmentsForTeacher("teacher", "2026/2027"))
            .thenReturn(List.of(math, math, inactive));
        AppUser user = new AppUser();
        user.setId(22L);
        StudentProfile student = new StudentProfile();
        student.setId(7L);
        student.setUser(user);
        student.setSchoolClass(schoolClass);
        student.setSurname("Іваненко");
        student.setName("Артем");
        when(students.findAllBySchoolClassIdInOrderBySurnameAscNameAsc(List.of(1L)))
            .thenReturn(List.of(student));
        ParentProfile parent = new ParentProfile();
        parent.setId(8L);
        parent.setSurname("Іваненко");
        parent.setName("Олена");
        parent.setPhone("+380671112233");
        parent.setEmail("parent@example.com");
        when(parents.findAllByStudent_IdInOrderByPrimaryContactDesc(List.of(7L)))
            .thenReturn(List.of(new StudentParentLink(student, parent, true)));

        var result = service().find("teacher");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().fullName()).isEqualTo("Іваненко Артем");
        assertThat(result.getFirst().userId()).isEqualTo(22L);
        assertThat(result.getFirst().subjects()).hasSize(1);
        assertThat(result.getFirst().subjects().getFirst().name()).isEqualTo("Математика");
        assertThat(result.getFirst().parents().getFirst().phone()).isEqualTo("+380671112233");
        verify(students).findAllBySchoolClassIdInOrderBySurnameAscNameAsc(List.of(1L));
    }

    @Test
    void noAssignmentsDoesNotLoadAnyStudentsOrContacts() {
        when(schedules.findJournalAssignmentsForTeacher("teacher", "2026/2027")).thenReturn(List.of());
        assertThat(service().find("teacher")).isEmpty();
        verifyNoInteractions(students, parents);
    }

    @Test
    void emptyClassDoesNotLoadParentContacts() {
        var schoolClass = new SchoolClass();
        schoolClass.setId(1L);
        when(schedules.findJournalAssignmentsForTeacher("teacher", "2026/2027"))
            .thenReturn(List.of(assignment(schoolClass, 10L, "Математика", true)));
        when(students.findAllBySchoolClassIdInOrderBySurnameAscNameAsc(anyList())).thenReturn(List.of());
        assertThat(service().find("teacher")).isEmpty();
        verifyNoInteractions(parents);
    }

    private ScheduleEntry assignment(SchoolClass schoolClass, Long id, String title, boolean active) {
        var subject = new Subject();
        subject.setId(id);
        subject.setTitle(title);
        var entry = new ScheduleEntry();
        entry.setSchoolClass(schoolClass);
        entry.setSubject(subject);
        entry.setActive(active);
        return entry;
    }
}
