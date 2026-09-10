package com.ediary.backend.school.repository;

import com.ediary.backend.school.entity.AcademicYear;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, String> {

    List<AcademicYear> findAllByOrderByValueAsc();
}
