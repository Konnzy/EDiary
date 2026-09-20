package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.StudentHomework;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentHomeworkRepository extends JpaRepository<StudentHomework, Long> {
    boolean existsByStudent_User_Id(Long userId);
}
