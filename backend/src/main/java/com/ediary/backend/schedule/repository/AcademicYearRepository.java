package com.ediary.backend.schedule.repository;

import com.ediary.backend.schedule.entity.AcademicYear;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, String> {

    List<AcademicYear> findAllByOrderByValueAsc();
}
