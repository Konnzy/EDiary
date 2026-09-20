package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    boolean existsByStudent_User_Id(Long userId);
}
