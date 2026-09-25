package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.StudentHomework;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentHomeworkRepository extends JpaRepository<StudentHomework, Long> {
    boolean existsByStudent_User_Id(Long userId);

    List<StudentHomework> findAllByStudentIdAndHomeworkIdIn(Long studentId, List<Long> homeworkIds);
    List<StudentHomework> findAllByHomeworkId(Long homeworkId);
    List<StudentHomework> findAllByHomeworkIdIn(List<Long> homeworkIds);
    Optional<StudentHomework> findByStudentIdAndHomeworkId(Long studentId, Long homeworkId);
}
