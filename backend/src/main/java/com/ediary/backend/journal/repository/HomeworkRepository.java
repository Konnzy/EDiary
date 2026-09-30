package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.Homework;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    List<Homework> findAllByLessonIdIn(List<Long> lessonIds);
    Optional<Homework> findFirstByLessonId(Long lessonId);
    List<Homework> findAllByDeadline(java.time.LocalDate deadline);
}
