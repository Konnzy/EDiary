package com.ediary.backend.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ediary.backend.journal.entity.Grade;
import com.ediary.backend.journal.entity.GradeType;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.user.entity.StudentProfile;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock private GradeRepository repository;

    @Test
    void changesGradeTypeWithoutCreatingSecondGradeForSameLesson() {
        StudentProfile student = new StudentProfile();
        student.setId(3L);
        Lesson lesson = new Lesson();
        lesson.setId(7L);
        lesson.setDate(LocalDate.of(2026, 12, 18));
        Grade existing = new Grade();
        existing.setType(GradeType.CURRENT);
        when(repository.findAllByStudentIdAndLessonId(3L, 7L)).thenReturn(List.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Grade result = new GradeService(repository).save(student, lesson, 11, GradeType.SEMESTER, "Підсумок");

        assertThat(result).isSameAs(existing);
        assertThat(result.getType()).isEqualTo(GradeType.SEMESTER);
        assertThat(result.getScore()).isEqualTo(11);
    }

    @Test
    void retagsExistingGradesWhenTeacherChangesLessonWorkType() {
        StudentProfile student = new StudentProfile();
        student.setId(3L);
        Lesson lesson = new Lesson();
        lesson.setId(7L);
        Grade grade = new Grade();
        grade.setStudent(student);
        grade.setType(GradeType.CURRENT);
        when(repository.findAllByLessonIdIn(List.of(7L))).thenReturn(List.of(grade));

        new GradeService(repository).retagLessonGrades(lesson, GradeType.SEMESTER);

        assertThat(grade.getType()).isEqualTo(GradeType.SEMESTER);
        verify(repository).saveAll(List.of(grade));
    }

    @Test
    void deletesSingleLegacyGradeWithDifferentType() {
        StudentProfile student = new StudentProfile();
        student.setId(3L);
        Lesson lesson = new Lesson();
        lesson.setId(7L);
        Grade grade = new Grade();
        grade.setType(GradeType.SEMESTER);
        when(repository.findAllByStudentIdAndLessonId(3L, 7L)).thenReturn(List.of(grade));

        new GradeService(repository).save(student, lesson, null, GradeType.CURRENT, null);

        verify(repository).delete(grade);
    }
}
