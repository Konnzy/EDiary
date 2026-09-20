package com.ediary.backend.journal.repository;

import com.ediary.backend.journal.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByStudent_User_Id(Long userId);
}
