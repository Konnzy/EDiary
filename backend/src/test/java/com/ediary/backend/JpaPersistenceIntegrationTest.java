package com.ediary.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.Grade;
import com.ediary.backend.journal.entity.GradeType;
import com.ediary.backend.journal.service.AssessmentPolicyService;
import com.ediary.backend.journal.service.AssessmentPolicyService.Mode;
import com.ediary.backend.journal.service.AssessmentPolicyService.Policy;
import com.ediary.backend.notification.dto.MessageDtos.Recipient;
import com.ediary.backend.notification.repository.MessageDirectory;
import com.ediary.backend.registration.dto.response.RegistrationRequestResponse;
import com.ediary.backend.registration.entity.RegistrationRequest;
import com.ediary.backend.registration.service.AdminNotificationService;
import com.ediary.backend.schedule.entity.AcademicYear;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.schedule.entity.LessonStatus;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.SchoolClass;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.entity.Weekday;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.user.admin.repository.UserImportLockRepository;
import com.ediary.backend.user.admin.entity.UserImportLock;
import com.ediary.backend.user.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.bootstrap-admin.enabled=false")
@EnabledIfEnvironmentVariable(named = "EDIARY_RUN_DB_TESTS", matches = "true")
@Transactional
class JpaPersistenceIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final String YEAR = "2098/2099";
    @Autowired EntityManager entities;
    @Autowired MessageDirectory directory;
    @Autowired AssessmentPolicyService policies;
    @Autowired AdminNotificationService notifications;
    @Autowired SubjectRepository subjects;
    @Autowired SchoolClassRepository classes;
    @Autowired ScheduleRepository schedules;
    @Autowired UserImportLockRepository importLock;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void recipientSearchRestrictsContactsToTheCurrentClassAndLinkedFamily() {
        var teacher = teacher("Леся", "Гнатюк");
        var subjectTeacher = teacher("Іван", "Антоненко");

        var year = entities.find(AcademicYear.class, YEAR);
        if (year == null) {
            year = new AcademicYear();
            year.setValue(YEAR);
            entities.persist(year);
        }
        var classroom = new SchoolClass();
        classroom.setNumber(9);
        classroom.setLetter("Я");
        classroom.setLimit(30);
        classroom.setClassTeacher(teacher);
        classroom.setAcademicYear(year);
        entities.persist(classroom);
        var subject = subject();
        subjectTeacher.getSubjects().add(subject);
        entities.flush();
        var entry = new ScheduleEntry();
        entry.setTeacher(subjectTeacher);
        entry.setSchoolClass(classroom);
        entry.setSubject(subject);
        entry.setAcademicYear(YEAR);
        entry.setWeekNumber(1);
        entry.setWeekday(Weekday.MONDAY);
        entry.setLessonNumber(1);
        entry.setStartTime(LocalTime.of(9, 0));
        entry.setEndTime(LocalTime.of(9, 40));
        entry.setEffectiveFrom(NOW.toLocalDate());
        entities.persist(entry);

        var student = new StudentProfile();
        student.setUser(account(UserRole.STUDENT));
        student.setName("Олег");
        student.setSurname("Сидоренко");
        student.setSchoolClass(classroom);
        student.setStatus(StudentStatus.STUDYING);
        student.setBirthDate(LocalDate.of(2011, 1, 1));
        student.setEnrollmentDate(NOW.toLocalDate());
        entities.persist(student);

        var parent = new ParentProfile();
        parent.setUser(account(UserRole.PARENT));
        parent.setName("Ірина");
        parent.setSurname("Сидоренко");
        parent.setBirthDate(LocalDate.of(1990, 1, 1));
        parent.setPhone("+380502222222");
        parent.setEmail(parent.getUser().getEmail());
        entities.persist(parent);
        entities.persist(new StudentParentLink(student, parent, true));

        Long teacherId = teacher.getUser().getId();
        Long studentId = student.getUser().getId();
        Long parentId = parent.getUser().getId();
        assertThat(directory.search(studentId, YEAR, "леся гнат", null))
            .containsExactly(new Recipient(teacherId, "Гнатюк Леся", UserRole.TEACHER, true));
        assertThat(directory.search(parentId, YEAR, "", teacherId)).extracting(Recipient::id).containsExactly(teacherId);
        assertThat(directory.search(parentId, YEAR, "", null))
            .containsExactly(new Recipient(teacherId, "Гнатюк Леся", UserRole.TEACHER, true),
                new Recipient(subjectTeacher.getUser().getId(), "Антоненко Іван", UserRole.TEACHER));
        assertThat(directory.search(teacherId, YEAR, "", null)).extracting(Recipient::id).containsExactlyInAnyOrder(studentId, parentId);
        assertThat(directory.search(studentId, YEAR, "", parentId)).isEmpty();
        assertThat(directory.search(parentId, "2097/2098", "", null)).isEmpty();
        assertThat(directory.identity(teacherId)).isEqualTo(new Recipient(teacherId, "Гнатюк Леся", UserRole.TEACHER));
        assertThat(classes.findIdsByYearAndNumberAndLetter(YEAR, 9, "я")).contains(classroom.getId());
        assertThat(classes.hasSchedule(classroom.getId())).isTrue();
        assertThat(subjects.hasTeachers(subject.getId())).isTrue();
        assertThat(subjects.hasSchedule(subject.getId())).isTrue();
        assertThat(subjects.isTeacherAssignmentUsedInSchedule(subject.getId(), subjectTeacher.getId())).isTrue();

        var lesson = new Lesson();
        lesson.setScheduleEntry(entry);
        lesson.setDate(NOW.toLocalDate());
        lesson.setStatus(LessonStatus.COMPLETED);
        lesson.setCreatedAt(NOW);
        lesson.setUpdatedAt(NOW);
        entities.persist(lesson);
        var grade = new Grade();
        grade.setStudent(student);
        grade.setLesson(lesson);
        grade.setType(GradeType.CURRENT);
        grade.setDate(lesson.getDate());
        grade.setScore(10);
        entities.persist(grade);
        assertThat(schedules.findIdsReferencedByLessons(List.of(entry.getId()))).containsExactly(entry.getId());
        assertThat(policies.hasResults(subject.getId())).isTrue();
        assertThatThrownBy(() -> policies.save(subject.getId(), new Policy(Mode.PASS_FAIL, false, false)))
            .isInstanceOfSatisfying(ApplicationException.class, error ->
                assertThat(error.getCode()).isEqualTo("ASSESSMENT_POLICY_HAS_RESULTS"));
    }

    @Test
    void hidesReviewedNotificationsOnceAndKeepsPendingOnes() {
        var admin = account(UserRole.ADMIN);
        var subject = subject();
        var reviewed = request(subject);
        reviewed.reject(admin, "Причина", NOW);
        var anotherReviewed = request(subject);
        anotherReviewed.reject(admin, "Причина", NOW.plusMinutes(1));
        var pending = request(subject);
        assertThat(subjects.hasRegistrationRequests(subject.getId())).isTrue();
        assertThat(notifications.feed(admin.getLogin()).items()).extracting(RegistrationRequestResponse::id)
            .contains(reviewed.getId(), anotherReviewed.getId(), pending.getId());

        notifications.dismiss(admin.getLogin(), reviewed.getId());
        notifications.dismiss(admin.getLogin(), reviewed.getId());
        assertThat(notifications.feed(admin.getLogin()).items()).extracting(RegistrationRequestResponse::id)
            .contains(anotherReviewed.getId(), pending.getId()).doesNotContain(reviewed.getId());

        notifications.clear(admin.getLogin());
        notifications.clear(admin.getLogin());
        assertThat(notifications.feed(admin.getLogin()).items()).extracting(RegistrationRequestResponse::id)
            .contains(pending.getId()).doesNotContain(reviewed.getId(), anotherReviewed.getId());
    }

    @Test
    void savesAssessmentPolicyUnderTheSubjectLock() {
        var subject = subject();
        Long id = subject.getId();
        assertThat(subjects.findIdsByTitleIgnoreCase(subject.getTitle().toUpperCase())).containsExactly(id);
        assertThat(policies.find(id)).isEqualTo(new Policy(Mode.NUMERIC, false, false));
        assertThat(policies.hasResults(id)).isFalse();
        policies.save(id, new Policy(Mode.PASS_FAIL, false, true));
        entities.flush();
        entities.clear();
        assertThat(policies.lockAndFind(id)).isEqualTo(new Policy(Mode.PASS_FAIL, false, true));
        policies.save(id, new Policy(Mode.NUMERIC, true, false));
        entities.flush();
        entities.clear();
        assertThat(policies.find(id)).isEqualTo(new Policy(Mode.NUMERIC, true, false));
    }

    @Test
    void anotherTransactionCannotAcquireTheImportLockWhileItIsHeld() {
        assertThat(importLock.acquire()).isPresent();
        var competingImport = new TransactionTemplate(transactions);
        competingImport.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> competingImport.executeWithoutResult(status -> entities.find(
            UserImportLock.class, 1, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 0))))
            .isInstanceOfAny(PessimisticLockException.class, LockTimeoutException.class);
    }

    private Subject subject() {
        var subject = new Subject();
        subject.setTitle("subject-" + UUID.randomUUID());
        entities.persist(subject);
        return subject;
    }

    private TeacherProfile teacher(String name, String surname) {
        var teacher = new TeacherProfile();
        teacher.setUser(account(UserRole.TEACHER));
        teacher.setName(name);
        teacher.setSurname(surname);
        teacher.setEmail(teacher.getUser().getEmail());
        teacher.setPhone("+380501111111");
        teacher.setBirthDate(LocalDate.of(1990, 1, 1));
        entities.persist(teacher);
        return teacher;
    }

    private RegistrationRequest request(Subject subject) {
        var request = new RegistrationRequest("Іван", "Петренко", null, UUID.randomUUID() + "@example.test",
            "+380503333333", LocalDate.of(1990, 1, 1), UserRole.TEACHER, null, subject, null, NOW);
        entities.persist(request);
        return request;
    }

    private AppUser account(UserRole role) {
        var user = new AppUser();
        user.setLogin("test-" + UUID.randomUUID());
        user.setEmail(user.getLogin() + "@example.test");
        user.setPasswordHash("test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(NOW);
        user.setUpdatedAt(NOW);
        entities.persist(user);
        return user;
    }
}
