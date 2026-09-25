package com.ediary.backend.journal.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.Grade;
import com.ediary.backend.journal.entity.GradeType;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.schedule.entity.Lesson;
import com.ediary.backend.user.entity.StudentProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;

    public void retagLessonGrades(Lesson lesson, GradeType type) {
        List<Grade> grades = gradeRepository.findAllByLessonIdIn(List.of(lesson.getId()));
        if (grades.stream().map(grade -> grade.getStudent().getId()).distinct().count() != grades.size()) {
            throw new ApplicationException(HttpStatus.CONFLICT, "MULTIPLE_GRADES_FOR_LESSON",
                "Для уроку є кілька оцінок одного учня; змінити тип роботи неможливо");
        }
        List<Grade> changed = grades.stream().filter(grade -> grade.getType() != type).toList();
        changed.forEach(grade -> grade.setType(type));
        if (!changed.isEmpty()) gradeRepository.saveAll(changed);
    }

    public Grade save(StudentProfile student, Lesson lesson, Integer score, GradeType type, String comment) {
        List<Grade> existing = gradeRepository.findAllByStudentIdAndLessonId(student.getId(), lesson.getId());
        Grade grade = existing.stream().filter(item -> item.getType() == type).findFirst().orElse(null);
        if (score == null) {
            if (grade == null && existing.size() == 1) grade = existing.getFirst();
            if (grade != null) gradeRepository.delete(grade);
            return null;
        }
        if (grade == null) {
            grade = existing.isEmpty() ? new Grade() : existing.getFirst();
            if (existing.isEmpty()) {
                grade.setStudent(student);
                grade.setLesson(lesson);
            }
        }
        grade.setType(type);
        grade.setDate(lesson.getDate());
        grade.setScore(score);
        grade.setComment(comment == null || comment.isBlank() ? null : comment.strip());
        return gradeRepository.save(grade);
    }
}
